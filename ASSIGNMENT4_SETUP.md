# Assignment 4 Setup and Usage Guide

This guide is based on your Assignment 4 brief and the current repo in this workspace.

## What is already available on this Mac

- Java: installed (`openjdk 17`)
- Maven: installed
- PostgreSQL 16: installed via Homebrew
- RabbitMQ: installed via Homebrew
- JMeter 5.6.3: installed
- JMeter WebSocket plugin: installed (`websocket-samplers 1.3.2`)

You do not need Docker for this repo on this machine because RabbitMQ is already installed locally.

## Tools you will use for Assignment 4

1. `JMeter`
   For the required before/after load tests and HTML reports.

2. `PostgreSQL`
   For message persistence and database optimization work like indexes and query tuning.

3. `RabbitMQ`
   For queue-backed chat delivery.

4. `Maven`
   To build the Java services.

5. `client-test`
   This repo also includes a fast Java load generator for quick smoke tests before you run bigger JMeter tests.

## One-time startup

Run these from the repo root:

```bash
cd "/Users/aryan/MASTERS/Distributed systems/ASSIGNMENT/Assignment_2"
```

### 1. Start PostgreSQL and RabbitMQ

```bash
brew services start postgresql@16
brew services start rabbitmq
```

### 2. Create the local database

If `chatdb` and `chatuser` do not already exist, run:

```bash
psql postgres
```

Then inside the `psql` prompt:

```sql
CREATE ROLE chatuser WITH LOGIN PASSWORD 'password123';
CREATE DATABASE chatdb OWNER chatuser;
\q
```

Apply the schema:

```bash
PGPASSWORD=password123 psql -h localhost -U chatuser -d chatdb -f database/schema.sql
```

### 3. Use RabbitMQ guest credentials locally

The repo defaults some services to `admin/password123`, but the helper scripts and local RabbitMQ setup are simplest with `guest/guest`.

Export these before starting the apps:

```bash
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=chatdb
export DB_USER=chatuser
export DB_PASSWORD=password123
```

### 4. Build the jars

These already built successfully once on this machine, but use these commands whenever you want a fresh build:

```bash
mvn -f server-v2/pom.xml -DskipTests package
mvn -f consumer/pom.xml -DskipTests package
mvn -f consumer-v3/pom.xml -DskipTests package
mvn -f client-test/pom.xml -DskipTests package
```

## How to run the system

### Option A: Simple local stack

This is the fastest way to get the baseline chat system running.

1. Create RabbitMQ exchange and queues:

```bash
bash deployment/rabbitmq-setup.sh localhost
```

2. Start one or two chat servers:

```bash
bash deployment/deploy-server.sh localhost 8080
bash deployment/deploy-server.sh localhost 8081
```

3. Start the consumer:

```bash
bash deployment/deploy-consumer.sh localhost 16 "http://localhost:8080,http://localhost:8081"
```

4. Check logs:

```bash
tail -f logs/server-8080.log
tail -f logs/server-8081.log
tail -f logs/consumer.log
```

### Option B: Optimized path for Assignment 4

Use this path if you want the database-backed optimized version while you measure index and batching improvements.

Run the optimized consumer directly:

```bash
java -jar consumer-v3/target/chat-consumer-v3-1.0.0.jar
```

If you want custom settings:

```bash
java \
  -DCONSUMER_WORKER_COUNT=10 \
  -DCONSUMER_BATCH_SIZE=2000 \
  -DCONSUMER_FLUSH_INTERVAL_MS=500 \
  -DRABBITMQ_PREFETCH=200 \
  -DDB_HOST=localhost \
  -DDB_PORT=5432 \
  -DDB_NAME=chatdb \
  -DDB_USER=chatuser \
  -DDB_PASSWORD=password123 \
  -DRABBITMQ_HOST=localhost \
  -DRABBITMQ_USERNAME=guest \
  -DRABBITMQ_PASSWORD=guest \
  -jar consumer-v3/target/chat-consumer-v3-1.0.0.jar
```

Keep `server-v2` running at the same time:

```bash
bash deployment/deploy-server.sh localhost 8080
```

## How to use JMeter

### Start the GUI

```bash
jmeter
```

### Build a basic HTTP test plan

Inside JMeter:

1. Add `Thread Group`
2. Add `HTTP Request Defaults`
3. Add one or more `HTTP Request` samplers
4. Add `Summary Report` and `Aggregate Report`

Suggested starting values:

- Threads: `100`
- Ramp-up: `60`
- Duration: `300` seconds

### Build a WebSocket test plan

The WebSocket plugin is already installed. In JMeter you should now see the WebSocket samplers.

Suggested structure:

1. `Thread Group`
2. `WebSocket Open Connection`
3. `Loop Controller`
4. `WebSocket Single Write Sampler`
5. `WebSocket Close`
6. `Summary Report`
7. `Aggregate Report`

Use these connection values:

- Server: `localhost`
- Port: `8080`
- Path: `/ws/chat`
- Protocol: `ws`

Example request body:

```json
{"roomId":"room1","userId":"user123","username":"aryan","message":"hello","messageType":"TEXT"}
```

### Run JMeter without the GUI

Use GUI mode only to create the test plan. For real measurements, run non-GUI mode:

```bash
jmeter -n -t your-test-plan.jmx -l results.jtl -e -o report-html
```

Open the HTML report:

```bash
open report-html/index.html
```

## Quick smoke testing with the repo's load client

Before big JMeter runs, you can quickly verify the chat server with the included Java load client:

```bash
java -jar client-test/target/chat-test-client-1.0.0.jar ws://localhost:8080/ws/chat 64 200000
```

There is also a helper script:

```bash
bash monitoring/run-local-benchmark.sh ws://localhost:8080/ws/chat 64 500000
```

## Metrics to collect for your report

For both before and after optimization runs, capture:

- Average response time
- p95 and p99 latency
- Throughput
- Error rate
- CPU and memory
- Queue depth
- Database performance

This repo already includes helper files you can reuse:

- `load-tests/configs/test1-baseline.properties`
- `load-tests/configs/test2-stress.properties`
- `load-tests/configs/test3-endurance.properties`
- `monitoring/collect-db-metrics.sh`
- `monitoring/collect-rabbitmq-depth.sh`

## Suggested Assignment 4 workflow

1. Pick the best Assignment 3 architecture as baseline.
2. Run a baseline JMeter test and save the numbers.
3. Implement optimization 1.
4. Run the same JMeter test again.
5. Implement optimization 2.
6. Run the same JMeter test again.
7. Compare baseline vs optimized metrics in your report.

## Good optimization choices for this repo

The repo already points toward strong Assignment 4 candidates:

- PostgreSQL indexes from `database/schema.sql`
- Batch DB writes in `consumer-v3`
- Hikari connection pool tuning in `consumer-v3/src/main/resources/application.properties`
- RabbitMQ prefetch tuning
- Server-side channel pooling in `server-v2`

## Useful health checks

Check the server:

```bash
curl http://localhost:8080/health
```

Check the consumer:

```bash
curl http://localhost:8090/health
curl http://localhost:8090/metrics
```

## Stop everything

```bash
pkill -f chat-server-v2
pkill -f chat-consumer
pkill -f chat-consumer-v3
brew services stop rabbitmq
brew services stop postgresql@16
```

## If something fails

- If RabbitMQ auth fails, make sure you exported `RABBITMQ_USERNAME=guest` and `RABBITMQ_PASSWORD=guest`.
- If DB connection fails, make sure `chatdb` exists and `database/schema.sql` was applied.
- If JMeter does not show WebSocket samplers, restart JMeter.
- If port `8080` is busy, start the server on `8081` and update your test URLs.
