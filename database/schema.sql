-- chatdb schema

CREATE TABLE IF NOT EXISTS messages (
    id              BIGSERIAL       PRIMARY KEY,
    message_id      UUID            NOT NULL UNIQUE,       -- idempotency key from RabbitMQ
    room_id         VARCHAR(50)     NOT NULL,
    user_id         VARCHAR(50)     NOT NULL,
    username        VARCHAR(100),
    message         TEXT,
    timestamp       TIMESTAMPTZ     NOT NULL,              -- when message was sent by client
    message_type    VARCHAR(10)     CHECK (message_type IN ('TEXT', 'JOIN', 'LEAVE')),
    server_id       VARCHAR(100),
    client_ip       VARCHAR(45),
    processed_at    TIMESTAMPTZ     DEFAULT NOW()          -- when consumer wrote to DB
);

CREATE TABLE IF NOT EXISTS dead_letter_messages (
    id                  BIGSERIAL       PRIMARY KEY,
    original_message    JSONB           NOT NULL,
    error_message       TEXT,
    retry_count         INT             DEFAULT 0,
    created_at          TIMESTAMPTZ     DEFAULT NOW(),
    last_retry_at       TIMESTAMPTZ
);

-- indexes
CREATE INDEX IF NOT EXISTS idx_messages_room_timestamp
    ON messages(room_id, timestamp);

CREATE INDEX IF NOT EXISTS idx_messages_user_timestamp
    ON messages(user_id, timestamp);

CREATE INDEX IF NOT EXISTS idx_messages_timestamp
    ON messages(timestamp);
