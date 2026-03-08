#!/usr/bin/env bash
set -euo pipefail

RABBITMQ_HOST="${1:-localhost}"
RABBITMQ_PORT="${2:-5672}"
EXCHANGE="${3:-chat.exchange}"
QUEUE_PREFIX="${4:-room.}"
ROOM_COUNT="${5:-20}"

echo "Setting up RabbitMQ topology on ${RABBITMQ_HOST}:${RABBITMQ_PORT}"

if ! command -v rabbitmqadmin >/dev/null 2>&1; then
  echo "rabbitmqadmin not found. Trying HTTP API with curl..."

  curl -s -u guest:guest -H "content-type:application/json" \
    -X PUT "http://${RABBITMQ_HOST}:15672/api/exchanges/%2F/${EXCHANGE}" \
    -d '{"type":"topic","durable":true}' >/dev/null

  for i in $(seq 1 "$ROOM_COUNT"); do
    q="${QUEUE_PREFIX}${i}"
    rk="${QUEUE_PREFIX}${i}"
    curl -s -u guest:guest -H "content-type:application/json" \
      -X PUT "http://${RABBITMQ_HOST}:15672/api/queues/%2F/${q}" \
      -d '{"durable":true,"arguments":{"x-message-ttl":120000,"x-max-length":50000}}' >/dev/null
    curl -s -u guest:guest -H "content-type:application/json" \
      -X POST "http://${RABBITMQ_HOST}:15672/api/bindings/%2F/e/${EXCHANGE}/q/${q}" \
      -d "{\"routing_key\":\"${rk}\"}" >/dev/null
  done
else
  rabbitmqadmin --host="$RABBITMQ_HOST" --port=15672 --username=guest --password=guest \
    declare exchange name="$EXCHANGE" type=topic durable=true
  for i in $(seq 1 "$ROOM_COUNT"); do
    q="${QUEUE_PREFIX}${i}"
    rk="${QUEUE_PREFIX}${i}"
    rabbitmqadmin --host="$RABBITMQ_HOST" --port=15672 --username=guest --password=guest \
      declare queue name="$q" durable=true arguments='{"x-message-ttl":120000,"x-max-length":50000}'
    rabbitmqadmin --host="$RABBITMQ_HOST" --port=15672 --username=guest --password=guest \
      declare binding source="$EXCHANGE" destination_type=queue destination="$q" routing_key="$rk"
  done
fi

echo "RabbitMQ setup complete"
