#!/usr/bin/env bash
set -e

############################################
# CONFIG
############################################
APP_NAME="myapp-board"

BLUE_PORT=9090
GREEN_PORT=9091

HEALTH_ENDPOINT="/env"
HEALTH_CHECK_TIMEOUT=60
HEALTH_INTERVAL=2

NGINX_UPSTREAM_FILE="/etc/nginx/conf.d/upstream.conf"

############################################
# STATE DETECT
############################################
echo "🔍 현재 상태 확인 중..."

CURRENT_UPSTREAM=$(curl -s http://localhost${HEALTH_ENDPOINT} || echo "blue")

if [[ "$CURRENT_UPSTREAM" == "blue" ]]; then
  ACTIVE_PORT=$BLUE_PORT
  STANDBY_PORT=$GREEN_PORT
  TARGET="green"
  PREVIOUS="blue"
else
  ACTIVE_PORT=$GREEN_PORT
  STANDBY_PORT=$BLUE_PORT
  TARGET="blue"
  PREVIOUS="green"
fi

echo "👉 ACTIVE: $PREVIOUS ($ACTIVE_PORT)"
echo "👉 DEPLOY TARGET: $TARGET ($STANDBY_PORT)"

############################################
# BUILD
############################################
echo "📦 빌드..."
./mvnw clean package -DskipTests

JAR_FILE=$(ls target/*.jar | head -n 1)

############################################
# STOP STANDBY
############################################
echo "🧹 기존 standby 정리..."
fuser -k ${STANDBY_PORT}/tcp || true

############################################
# START NEW VERSION
############################################
echo "🚀 ${TARGET} 실행..."

nohup java -jar \
  -Dserver.port=${STANDBY_PORT} \
  -Dspring.profiles.active=${TARGET} \
  ${JAR_FILE} > ${TARGET}.log 2>&1 &

NEW_PID=$!

echo "PID: $NEW_PID"

############################################
# HEALTH CHECK
############################################
echo "⏳ Health Check 시작..."

START_TIME=$(date +%s)
HEALTH_OK=false

while true; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:${STANDBY_PORT}${HEALTH_ENDPOINT} || true)

  if [[ "$STATUS" == "200" ]]; then
    HEALTH_OK=true
    echo "✅ Health Check 성공!"
    break
  fi

  NOW=$(date +%s)
  ELAPSED=$((NOW - START_TIME))

  if [[ $ELAPSED -gt $HEALTH_CHECK_TIMEOUT ]]; then
    echo "❌ Health Check 실패 (timeout)"
    break
  fi

  echo "⏳ 대기 중... (${ELAPSED}s)"
  sleep $HEALTH_INTERVAL
done

############################################
# FAIL → ROLLBACK
############################################
if [[ "$HEALTH_OK" != true ]]; then

  echo "🚨 배포 실패 → 자동 롤백 시작"

  # 1. 신규 프로세스 종료
  kill -9 $NEW_PID || true

  # 2. 기존 upstream 유지 (아무것도 안 바꿈)
  echo "🔒 기존 서비스 유지 (롤백 완료)"

  exit 1
fi

############################################
# SUCCESS → SWITCH TRAFFIC
############################################
echo "🔀 트래픽 전환..."

cat <<EOF > /tmp/upstream.conf
upstream myapp {
    server 127.0.0.1:${STANDBY_PORT};
}
EOF

sudo cp /tmp/upstream.conf ${NGINX_UPSTREAM_FILE}
sudo nginx -s reload

echo "✅ Nginx 전환 완료 → ${TARGET}"

############################################
# SAFE GRACE SHUTDOWN OLD
############################################
echo "⏳ 기존 서버 종료 대기..."
sleep 10

echo "🧹 기존 서버 종료"

fuser -k ${ACTIVE_PORT}/tcp || true

############################################
# FINAL STATE CHECK
############################################
echo "🎉 배포 성공!"
echo "ACTIVE: ${TARGET} (${STANDBY_PORT})"