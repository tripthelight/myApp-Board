#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_DIR="${MYAPP_INFRA_DIR:-$HOME/myApp-Infra}"
NGINX_CONTAINER="myapp-nginx"
NETWORK_NAME="myapp-network"
IMAGE_NAME="${IMAGE_NAME:-myapp-board}"
IMAGE_TAG="${IMAGE_TAG:-manual-$(date +%Y%m%d%H%M%S)}"
DRAIN_SECONDS="${DRAIN_SECONDS:-10}"
STABILIZATION_SECONDS="${STABILIZATION_SECONDS:-30}"
CHECK_INTERVAL_SECONDS="${CHECK_INTERVAL_SECONDS:-2}"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required command not found: $1" >&2
        exit 1
    fi
}

require_command docker
require_command java

if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running or the current user cannot access it." >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose v2 is not available." >&2
    exit 1
fi

if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
    echo "Docker network does not exist: $NETWORK_NAME" >&2
    exit 1
fi

if [ "$(docker inspect --format '{{.State.Running}}' "$NGINX_CONTAINER" 2>/dev/null || true)" != true ]; then
    echo "Nginx container is not running: $NGINX_CONTAINER" >&2
    exit 1
fi

PROMOTE_SCRIPT="$INFRA_DIR/scripts/promote-board-upstream.sh"

if [ ! -x "$PROMOTE_SCRIPT" ]; then
    echo "Nginx promotion script is not executable: $PROMOTE_SCRIPT" >&2
    exit 1
fi

loaded_config="$(docker exec "$NGINX_CONTAINER" nginx -T 2>&1)"

if grep -Fq 'server myapp-board-blue-1:8080' <<< "$loaded_config"; then
    CURRENT=blue
    TARGET=green
elif grep -Fq 'server myapp-board-green-1:8080' <<< "$loaded_config"; then
    CURRENT=green
    TARGET=blue
else
    echo "Could not determine the active Board color." >&2
    exit 1
fi

TARGET_COMPOSE_FILE="$PROJECT_DIR/deploy/docker-compose-$TARGET.yml"
SWITCHED=false

if [[ ! "$STABILIZATION_SECONDS" =~ ^[1-9][0-9]*$ ]] || \
   [[ ! "$CHECK_INTERVAL_SECONDS" =~ ^[1-9][0-9]*$ ]]; then
    echo "Stabilization and check interval values must be positive integers." >&2
    exit 1
fi

cleanup_on_error() {
    exit_code=$?

    if [ "$exit_code" -eq 2 ]; then
        echo "Infra rollback failed. Keeping both colors for investigation." >&2
    elif [ "$SWITCHED" != true ]; then
        echo "Deployment failed before the Nginx switch. Removing the $TARGET containers." >&2
        IMAGE_NAME="$IMAGE_NAME" IMAGE_TAG="$IMAGE_TAG" \
            docker compose -f "$TARGET_COMPOSE_FILE" down || true
    else
        echo "Deployment failed after the Nginx switch. Both colors are being kept." >&2
    fi

    exit "$exit_code"
}

trap cleanup_on_error ERR

cd "$PROJECT_DIR"

echo "Deployment plan: $CURRENT -> $TARGET"
echo "Docker image: $IMAGE_NAME:$IMAGE_TAG"

echo "[1/8] Build Spring Boot application"
./mvnw --batch-mode --errors clean package

echo "[2/8] Build Docker image"
docker build --tag "$IMAGE_NAME:$IMAGE_TAG" .

echo "[3/8] Start two $TARGET containers"
export IMAGE_NAME IMAGE_TAG
docker compose -f "$TARGET_COMPOSE_FILE" config >/dev/null
docker compose -f "$TARGET_COMPOSE_FILE" up -d --force-recreate

echo "[4/8] Check both $TARGET containers"
for instance in 1 2; do
    container="myapp-board-$TARGET-$instance"
    ready=false

    for attempt in $(seq 1 30); do
        echo "Health check $container: $attempt/30"

        if docker run --rm --network "$NETWORK_NAME" busybox:1.36 \
            wget -q -T 2 -O /dev/null "http://$container:8080/hc"; then
            ready=true
            break
        fi

        sleep 2
    done

    if [ "$ready" != true ]; then
        docker logs "$container" || true
        echo "Health check failed: $container" >&2
        false
    fi
done

echo "[5/8] Promote Nginx to $TARGET and stabilize"
STABILIZATION_SECONDS="$STABILIZATION_SECONDS" \
CHECK_INTERVAL_SECONDS="$CHECK_INTERVAL_SECONDS" \
    "$PROMOTE_SCRIPT" "$TARGET"
SWITCHED=true

echo "[6/8] Promotion verified by Infra"

echo "[7/8] Wait ${DRAIN_SECONDS}s before stopping $CURRENT"
sleep "$DRAIN_SECONDS"

echo "[8/8] Stop the inactive $CURRENT containers"
IMAGE_NAME="$IMAGE_NAME" IMAGE_TAG="$IMAGE_TAG" \
    "$PROJECT_DIR/scripts/stop-inactive-color.sh" "$CURRENT"

trap - ERR

echo
docker ps --filter 'name=myapp-board-' \
    --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'

echo
echo "Deployment complete: $CURRENT -> $TARGET"
