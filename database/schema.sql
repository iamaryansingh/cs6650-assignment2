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

-- Optimization 1: Covering indexes — include all queried columns so the
-- executor can satisfy reads entirely from the index without heap fetches.

-- Room query: WHERE room_id=? AND timestamp BETWEEN ? AND ? ORDER BY timestamp
-- INCLUDE covers the SELECT list (message_id, user_id, username, message, message_type)
CREATE INDEX IF NOT EXISTS idx_messages_room_timestamp
    ON messages(room_id, timestamp ASC)
    INCLUDE (message_id, user_id, username, message, message_type);

-- User query: WHERE user_id=? [AND timestamp BETWEEN ? AND ?] ORDER BY timestamp DESC
-- INCLUDE covers SELECT list + room_id (needed for getUserRooms GROUP BY room_id)
CREATE INDEX IF NOT EXISTS idx_messages_user_timestamp
    ON messages(user_id, timestamp DESC)
    INCLUDE (message_id, room_id, username, message, message_type);

-- Active-users query: COUNT(DISTINCT user_id) WHERE timestamp BETWEEN ? AND ?
-- INCLUDE user_id eliminates heap fetch for the DISTINCT aggregation
CREATE INDEX IF NOT EXISTS idx_messages_timestamp_userid
    ON messages(timestamp)
    INCLUDE (user_id);
