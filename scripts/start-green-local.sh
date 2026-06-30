#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_NAME="${IMAGE_NAME:-myapp-board}"
IMAGE_TAG="${IMAGE_TAG:-step8}"
NETWORK_NAME="myapp-network"
COMPOSE_FILE="$PROJECT_DIR/deploy/docker-compose-green.yml"

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required command not found: $1" >&2
        exit 1
    fi
}

require_command docker

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

if ! docker image inspect "$IMAGE_NAME:$IMAGE_TAG" >/dev/null 2>&1; then
    echo "Docker image does not exist: $IMAGE_NAME:$IMAGE_TAG" >&2
    exit 1
fi

cd "$PROJECT_DIR"

echo "[1/3] Validate Green Docker Compose configuration"
export IMAGE_NAME IMAGE_TAG
docker compose -f "$COMPOSE_FILE" config >/dev/null

echo "[2/3] Start two Green containers"
docker compose -f "$COMPOSE_FILE" up -d --force-recreate

echo "[3/3] Check each Green container"
for container in myapp-board-green-1 myapp-board-green-2; do
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
        exit 1
    fi
done

echo
docker ps --filter 'name=myapp-board-green-' \
    --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'

echo
echo "Green startup complete. Nginx still routes traffic to Blue."
