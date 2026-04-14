-- Migration: upgrade basic indexes to covering indexes (Assignment 4, Optimization 1)
-- Run on the live EC2 PostgreSQL instance.
-- Uses CONCURRENTLY so no table lock is taken during index build.
-- Must be run OUTSIDE a transaction block (psql \i or run each statement separately).

-- 1. Drop old basic indexes
DROP INDEX CONCURRENTLY IF EXISTS idx_messages_room_timestamp;
DROP INDEX CONCURRENTLY IF EXISTS idx_messages_user_timestamp;
DROP INDEX CONCURRENTLY IF EXISTS idx_messages_timestamp;

-- 2. Room covering index
--    Covers: WHERE room_id=? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC
--    INCLUDE eliminates heap fetch for message_id, user_id, username, message, message_type
CREATE INDEX CONCURRENTLY idx_messages_room_timestamp
    ON messages(room_id, timestamp ASC)
    INCLUDE (message_id, user_id, username, message, message_type);

-- 3. User covering index
--    Covers: WHERE user_id=? [AND timestamp BETWEEN ? AND ?] ORDER BY timestamp DESC
--    INCLUDE also covers room_id for getUserRooms GROUP BY room_id
CREATE INDEX CONCURRENTLY idx_messages_user_timestamp
    ON messages(user_id, timestamp DESC)
    INCLUDE (message_id, room_id, username, message, message_type);

-- 4. Timestamp + user_id covering index
--    Covers: COUNT(DISTINCT user_id) WHERE timestamp BETWEEN ? AND ?
--    INCLUDE user_id means the DISTINCT aggregation never touches the heap
CREATE INDEX CONCURRENTLY idx_messages_timestamp_userid
    ON messages(timestamp)
    INCLUDE (user_id);

-- Verify: run EXPLAIN (ANALYZE, BUFFERS) on your hot queries and confirm
-- "Index Only Scan" appears (no "Heap Fetches" or a very low count).
-- Example:
--   EXPLAIN (ANALYZE, BUFFERS)
--   SELECT COUNT(*) FROM messages WHERE room_id = 'room.1'
--     AND timestamp BETWEEN NOW() - INTERVAL '1 hour' AND NOW();
