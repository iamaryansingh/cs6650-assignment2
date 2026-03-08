#!/usr/bin/env bash
set -euo pipefail

WS_URL="${1:-ws://localhost:8080/ws/chat}"
THREADS="${2:-64}"
TOTAL="${3:-500000}"

java -jar client-test/target/chat-test-client-1.0.0.jar "$WS_URL" "$THREADS" "$TOTAL"
