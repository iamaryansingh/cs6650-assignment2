#!/usr/bin/env bash
set -euo pipefail

# 1) RabbitMQ
if ! docker ps --format '{{.Names}}' | grep -q '^rabbitmq-a2$'; then
  docker run -d --name rabbitmq-a2 -p 5672:5672 -p 15672:15672 rabbitmq:3-management
  sleep 8
fi

# 2) Build
mvn -f server-v2/pom.xml clean package
mvn -f consumer/pom.xml clean package
mvn -f client-test/pom.xml clean package

# 3) Topology
bash deployment/rabbitmq-setup.sh localhost

# 4) Servers + consumer
bash deployment/deploy-server.sh localhost 8080
bash deployment/deploy-server.sh localhost 8081
bash deployment/deploy-consumer.sh localhost 16 "http://localhost:8080,http://localhost:8081"

echo "Local stack launched"
