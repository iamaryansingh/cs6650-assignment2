-- CS6650 Assignment 3 - PostgreSQL Schema
-- Database: chatdb

-- ============================================================
-- TABLE: messages
-- Primary persistence table for all chat messages
-- ============================================================
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

-- ============================================================
-- TABLE: dead_letter_messages
-- Stores messages that failed to persist after all retries
-- ============================================================
CREATE TABLE IF NOT EXISTS dead_letter_messages (
    id                  BIGSERIAL       PRIMARY KEY,
    original_message    JSONB           NOT NULL,
    error_message       TEXT,
    retry_count         INT             DEFAULT 0,
    created_at          TIMESTAMPTZ     DEFAULT NOW(),
    last_retry_at       TIMESTAMPTZ
);

-- ============================================================
-- INDEXES
-- Each index is aligned to a specific core query target
-- ============================================================

-- Core Query 1: "Get messages for a room in time range" -> target < 100ms
-- Composite on (room_id, timestamp) allows range scan within a room
CREATE INDEX IF NOT EXISTS idx_messages_room_timestamp
    ON messages(room_id, timestamp);

-- Core Query 2: "Get user's message history" -> target < 200ms
-- Composite on (user_id, timestamp) covers filtering + ordering in one index
CREATE INDEX IF NOT EXISTS idx_messages_user_timestamp
    ON messages(user_id, timestamp);

-- Core Query 3: "Count active users in time window" -> target < 500ms
-- Standalone timestamp index allows time-range scan for COUNT(DISTINCT user_id)
CREATE INDEX IF NOT EXISTS idx_messages_timestamp
    ON messages(timestamp);

-- Core Query 4: "Get rooms user has participated in" -> target < 50ms
-- Covered by idx_messages_user_timestamp (same user_id prefix, groups by room_id)

-- Analytics: top users / top rooms use full-table aggregations,
-- covered by the above indexes via index-only scans on hot data.

-- NOTE: message_id UNIQUE constraint automatically creates a btree index,
-- which also handles ON CONFLICT (message_id) DO NOTHING efficiently.
