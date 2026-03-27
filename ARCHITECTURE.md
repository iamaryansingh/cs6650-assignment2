# CS6650 Assignment 2 - Architecture Document

## 1. System Architecture Diagram

```mermaid
flowchart LR
    C[Load Client] --> LB[AWS ALB]
    LB --> S1[WebSocket Server 1]
    LB --> S2[WebSocket Server 2]
    LB --> S3[WebSocket Server 3]
    LB --> S4[WebSocket Server 4]

    S1 --> EX[(RabbitMQ Exchange\nchat.exchange)]
    S2 --> EX
    S3 --> EX
    S4 --> EX

    EX --> Q1[(room.1)]
    EX --> Q2[(room.2)]
    EX --> Q20[(room.20)]

    Q1 --> CON[Consumer Pool]
    Q2 --> CON
    Q20 --> CON

    CON --> B1[Server Internal Broadcast API]
    B1 --> WS[(Connected WS Sessions)]
```

## 2. Message Flow Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant Server as WebSocket Server
    participant MQ as RabbitMQ
    participant Consumer
    participant Broadcast as /internal/broadcast

    Client->>Server: WebSocket message (roomId, userId, type, payload)
    Server->>Server: Validate + enrich (messageId, timestamp, serverId, clientIp)
    Server->>MQ: Publish to chat.exchange with key room.{roomId}
    Server-->>Client: ACK response (status, messageId, roomId)

    MQ->>Consumer: Deliver from room.{id} queue
    Consumer->>Consumer: Idempotency check (messageId)
    Consumer->>Broadcast: POST /internal/broadcast
    Broadcast->>Broadcast: Room lookup + per-session synchronized send
    Broadcast-->>Consumer: 200 OK
    Consumer->>MQ: ACK message
```

## 3. Queue Topology Design

- Broker: RabbitMQ on dedicated EC2 instance.
- Exchange: `chat.exchange` (type: topic, durable).
- Routing key: `room.{roomId}`.
- Queues: `room.1` to `room.20` (durable).
- Queue controls:
  - `x-message-ttl`: 120000 ms
  - `x-max-length`: 50000
- Delivery mode: persistent messages.
- Consumer prefetch: configurable (`rabbitmq.prefetch`).

## 4. Consumer Threading Model

- Single AMQP connection: `consumer-pool`.
- N worker threads (configurable with `consumer.worker-count`; current default 10).
- Each worker owns one channel and consumes a fair subset of room queues.
- Message handling per worker:
  1. Deserialize message.
  2. Deduplicate using in-memory TTL cache.
  3. Forward to server internal broadcast endpoint(s).
  4. ACK on success.
  5. NACK with `requeue=false` on terminal failure.

## 5. Load Balancing Configuration

- Front door: AWS Application Load Balancer.
- Target group: all WebSocket server instances.
- Sticky sessions: enabled for stable WebSocket affinity.
- Health check:
  - Path: `/health`
  - Interval: 30s
  - Timeout: 5s
  - Healthy threshold: 2
  - Unhealthy threshold: 3
- Idle timeout: > 60 seconds for WebSocket support.

## 6. Failure Handling Strategies

- Producer-side:
  - RabbitMQ channel pooling to avoid connection churn.
  - Publisher confirms enabled.
  - Publish failure tracked in metrics (`publishedFailed`).
- Consumer-side:
  - Retry wrapper for broadcast HTTP calls.
  - Idempotency cache to ignore duplicate messageIds.
  - Terminal failures are dropped (`requeue=false`) to avoid poison-message loops.
- WebSocket broadcast:
  - Per-session send lock prevents concurrent writes (`TEXT_PARTIAL_WRITING` issue).
- Health/observability:
  - Server `/health`: rooms, sessions, activeUsers, publish/broadcast counters.
  - Consumer `/health` and `/metrics`: processed, forwarded, failed, throughput.

## 7. Design Notes (Brief)

- Assignment 2 extends Assignment 1 by decoupling ingest from distribution via RabbitMQ.
- This architecture provides at-least-once delivery with idempotent processing.
- Horizontal scaling is done by adding server instances behind ALB and tuning consumer concurrency.
