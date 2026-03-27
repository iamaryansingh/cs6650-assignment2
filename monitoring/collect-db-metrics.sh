#!/bin/bash
# Collect PostgreSQL metrics every 5 seconds during a load test.
# Requires: DB_HOST, DB_PASSWORD env vars (DB_USER defaults to chatuser)
# Usage: ./collect-db-metrics.sh <output-file> <duration-seconds>
# Example: ./collect-db-metrics.sh load-tests/results/test1-db-metrics.csv 600

OUTPUT_FILE=${1:-"db-metrics.csv"}
DURATION=${2:-600}
DB_HOST=${DB_HOST:-localhost}
DB_USER=${DB_USER:-chatuser}
DB_NAME=${DB_NAME:-chatdb}

if [ -z "$DB_PASSWORD" ]; then
  echo "ERROR: DB_PASSWORD must be set"
  exit 1
fi

echo "Collecting DB metrics -> $OUTPUT_FILE for ${DURATION}s"
echo "timestamp,active_connections,total_messages,cache_hit_ratio_pct,lock_waits,db_size_mb" > "$OUTPUT_FILE"

END_TIME=$(($(date +%s) + DURATION))

while [ "$(date +%s)" -lt "$END_TIME" ]; do
  TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

  METRICS=$(PGPASSWORD=$DB_PASSWORD psql -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" -t -A -F',' -c "
    SELECT
      (SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND datname = '$DB_NAME'),
      (SELECT count(*) FROM messages),
      (SELECT ROUND(
        100.0 * SUM(blks_hit) / NULLIF(SUM(blks_hit) + SUM(blks_read), 0), 2
       ) FROM pg_stat_database WHERE datname = '$DB_NAME'),
      (SELECT count(*) FROM pg_locks WHERE NOT granted),
      (SELECT ROUND(pg_database_size('$DB_NAME') / 1024.0 / 1024.0, 2))
  " 2>/dev/null | tr -d ' ')

  echo "$TIMESTAMP,$METRICS" >> "$OUTPUT_FILE"
  sleep 5
done

echo "Done. Results in $OUTPUT_FILE"
