#!/usr/bin/env bash
set -euo pipefail

RABBITMQ_HOST="${1:-localhost}"
PORT="${2:-8080}"
JAR_PATH="${3:-./server-v2/target/chat-server-v2-1.0.0.jar}"
INSTANCE_ID="${4:-server-${PORT}}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Missing jar: $JAR_PATH"
  echo "Build with: mvn -f server-v2/pom.xml clean package"
  exit 1
fi

mkdir -p logs

pkill -f "chat-server-v2.*${PORT}" || true

nohup java \
  -Xms256m -Xmx512m \
  -Dserver.port="$PORT" \
  -DSERVER_INSTANCE_ID="$INSTANCE_ID" \
  -DRABBITMQ_HOST="$RABBITMQ_HOST" \
  -DRABBITMQ_CHANNEL_POOL_SIZE=250 \
  -DSERVER_TOMCAT_THREADS_MAX=250 \
  -DSERVER_TOMCAT_THREADS_MIN_SPARE=20 \
  -jar "$JAR_PATH" > "logs/server-${PORT}.log" 2>&1 &

sleep 3
curl -s "http://localhost:${PORT}/health" || true
printf "\nserver started on %s\n" "$PORT"
