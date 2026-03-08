#!/usr/bin/env bash
set -euo pipefail

HOST="${1:-localhost}"
INTERVAL="${2:-2}"
DURATION="${3:-120}"
OUT="queue-depth-$(date +%Y%m%d-%H%M%S).csv"

echo "timestamp,total_messages" > "$OUT"
START=$(date +%s)
END=$((START + DURATION))

while [[ $(date +%s) -lt $END ]]; do
  ts=$(date +%H:%M:%S)
  total=$(curl -s -u guest:guest "http://${HOST}:15672/api/queues" | \
    awk -F'"messages":' '{sum += $2+0} END {print sum+0}')
  echo "$ts,$total" >> "$OUT"
  sleep "$INTERVAL"
done

echo "saved $OUT"
