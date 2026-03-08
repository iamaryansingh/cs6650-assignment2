# CS6650 Assignment 2 - Queue-backed Distributed Chat

This repository contains a local-first implementation of Assignment 2:

- `server-v2`: WebSocket ingest server that publishes chat events to RabbitMQ.
- `consumer`: Multi-threaded RabbitMQ consumer that forwards events to WebSocket servers for room broadcast.
- `client-test`: Load client for throughput testing.
- `deployment`: Helper scripts for local and EC2 deployment.
- `monitoring`: RabbitMQ and runtime metric collection scripts.

## Quick Start (Local)

1. Start RabbitMQ with management UI:

```bash
docker run -d --name rabbitmq-a2 -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

2. Build all modules:

```bash
mvn -f server-v2/pom.xml clean package
mvn -f consumer/pom.xml clean package
mvn -f client-test/pom.xml clean package
```

3. Initialize exchange/queues:

```bash
bash deployment/rabbitmq-setup.sh localhost
```

4. Start server(s):

```bash
bash deployment/deploy-server.sh localhost 8080
# Optional second instance
bash deployment/deploy-server.sh localhost 8081
```

5. Start consumer:

```bash
# Fan-out broadcast to all running server instances
bash deployment/deploy-consumer.sh localhost 16 "http://localhost:8080,http://localhost:8081"
```

6. Run load test:

```bash
java -jar client-test/target/chat-test-client-1.0.0.jar ws://localhost:8080/ws/chat 64 200000
```

## Notes

- WebSocket clients send JSON messages with `roomId`, `userId`, `username`, `message`, `messageType`.
- The server publishes to exchange `chat.exchange` with routing key `room.{roomId}`.
- Consumer supports at-least-once processing, retries, and duplicate protection (messageId cache).
