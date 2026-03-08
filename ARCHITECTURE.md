# Architecture

## Components

1. WebSocket Producers (`server-v2`)
- Accept client WebSocket traffic at `/ws/chat`
- Validate and enrich messages (serverId, timestamp, messageId)
- Publish to RabbitMQ topic exchange `chat.exchange`

2. RabbitMQ
- Topic exchange routes by room: `room.{roomId}`
- One durable queue per room (`room.1` to `room.20`)
- Dead-letter setup for failed messages

3. Consumer Pool (`consumer`)
- Configurable worker count
- Workers subscribe to room queues with bounded prefetch
- For each consumed message, forwards to all server internal broadcast endpoints
- Ack only after successful forward; otherwise nacks with retry

4. Room Broadcast (`server-v2` internal API)
- Endpoint `/internal/broadcast`
- Delivers to local in-memory room sessions only
- Dedupe cache avoids duplicate deliveries under retries

## Message Flow

Client -> WebSocket Server -> RabbitMQ Exchange -> Room Queue -> Consumer -> Server Broadcast API -> WebSocket Sessions

## Throughput/Scalability additions

- Channel pool with publisher confirms
- Consumer thread pool and configurable prefetch
- Dedupe caches on both consumer and server paths
- Backpressure-aware async WebSocket send
- Internal batch-friendly forwarding abstraction
