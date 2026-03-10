#!/usr/bin/env bash
set -euo pipefail

RABBITMQ_HOST="${1:-localhost}"
WORKER_COUNT="${2:-10}"
SERVER_ENDPOINTS="${3:-http://localhost:8080}"
JAR_PATH="${4:-./consumer/target/chat-consumer-1.0.0.jar}"

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Missing jar: $JAR_PATH"
  echo "Build with: mvn -f consumer/pom.xml clean package"
  exit 1
fi

mkdir -p logs
pkill -f "chat-consumer" || true

nohup java \
  -Xms256m -Xmx512m \
  -DRABBITMQ_HOST="$RABBITMQ_HOST" \
  -DCONSUMER_WORKER_COUNT="$WORKER_COUNT" \
  -DRABBITMQ_PREFETCH=20 \
  -DCONSUMER_REQUEST_TIMEOUT_MS=10000 \
  -DCONSUMER_MAX_RETRIES=2 \
  -DCONSUMER_SERVER_ENDPOINTS="$SERVER_ENDPOINTS" \
  -jar "$JAR_PATH" > logs/consumer.log 2>&1 &

sleep 2
echo "consumer started: workers=${WORKER_COUNT} endpoints=${SERVER_ENDPOINTS}"
