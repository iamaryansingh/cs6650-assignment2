package com.cs6650.server.metrics;

import com.cs6650.server.metrics.dto.ActiveUsersResult;
import com.cs6650.server.metrics.dto.AnalyticsSummary;
import com.cs6650.server.metrics.dto.RoomActivity;
import com.cs6650.server.metrics.dto.RoomMessageResult;
import com.cs6650.server.metrics.dto.UserMessageResult;
import com.cs6650.server.metrics.dto.UserRoomsResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Assignment 3 Part 1: Metrics queries using JdbcTemplate.
 * JdbcTemplate has no startup cost — connects lazily on first query.
 * Simple 5s in-memory cache for analytics queries (Part 3).
 */
@Service
public class MetricsService {

  private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
  private static final long CACHE_TTL_MS = 5000;

  private final JdbcTemplate jdbc;
  private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

  public MetricsService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ================================================================
  // CORE QUERY 1: Room messages in time range  (target < 100ms)
  // ================================================================
  public RoomMessageResult getRoomMessages(String roomId, Instant start, Instant end) {
    long t0 = System.nanoTime();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT message_id, room_id, user_id, username, message, timestamp, message_type " +
        "FROM messages WHERE room_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC",
        roomId, Timestamp.from(start), Timestamp.from(end));
    long ms = nanos(t0);
    warnIfSlow("getRoomMessages", ms, 100);
    return new RoomMessageResult(roomId, start.toString(), end.toString(), rows.size(), rows, ms);
  }

  // ================================================================
  // CORE QUERY 2: User message history  (target < 200ms)
  // ================================================================
  public UserMessageResult getUserMessages(String userId, Instant start, Instant end) {
    long t0 = System.nanoTime();
    List<Map<String, Object>> rows;
    if (start != null && end != null) {
      rows = jdbc.queryForList(
          "SELECT message_id, room_id, user_id, username, message, timestamp, message_type " +
          "FROM messages WHERE user_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp DESC",
          userId, Timestamp.from(start), Timestamp.from(end));
    } else {
      rows = jdbc.queryForList(
          "SELECT message_id, room_id, user_id, username, message, timestamp, message_type " +
          "FROM messages WHERE user_id = ? ORDER BY timestamp DESC",
          userId);
    }
    long ms = nanos(t0);
    warnIfSlow("getUserMessages", ms, 200);
    return new UserMessageResult(userId, rows.size(), rows, ms);
  }

  // ================================================================
  // CORE QUERY 3: Count active users in time window  (target < 500ms)
  // ================================================================
  public ActiveUsersResult getActiveUsers(Instant start, Instant end) {
    long t0 = System.nanoTime();
    Long count = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT user_id) FROM messages WHERE timestamp BETWEEN ? AND ?",
        Long.class, Timestamp.from(start), Timestamp.from(end));
    long ms = nanos(t0);
    warnIfSlow("getActiveUsers", ms, 500);
    return new ActiveUsersResult(count == null ? 0 : count, start.toString(), end.toString(), ms);
  }

  // ================================================================
  // CORE QUERY 4: Rooms a user participated in  (target < 50ms)
  // ================================================================
  public UserRoomsResult getUserRooms(String userId) {
    long t0 = System.nanoTime();
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT room_id, MAX(timestamp) AS last_activity, COUNT(*) AS message_count " +
        "FROM messages WHERE user_id = ? GROUP BY room_id ORDER BY last_activity DESC",
        userId);
    List<RoomActivity> rooms = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      rooms.add(new RoomActivity(
          (String) row.get("room_id"),
          row.get("last_activity") == null ? null : row.get("last_activity").toString(),
          ((Number) row.get("message_count")).longValue()));
    }
    long ms = nanos(t0);
    warnIfSlow("getUserRooms", ms, 50);
    return new UserRoomsResult(userId, rooms, ms);
  }

  // ================================================================
  // ANALYTICS: Combined summary — cached for 5 seconds
  // ================================================================
  public AnalyticsSummary getAnalyticsSummary(int topN) {
    String cacheKey = "analytics-" + topN;
    CachedResult cached = cache.get(cacheKey);
    if (cached != null && !cached.isExpired()) {
      return (AnalyticsSummary) cached.value;
    }

    long t0 = System.nanoTime();

    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class);
    if (total == null) total = 0L;

    // Average messages per second from bucketed counts
    List<Map<String, Object>> perSecond = jdbc.queryForList(
        "SELECT date_trunc('second', timestamp) AS ts, COUNT(*) AS cnt FROM messages GROUP BY ts");
    double avgPerSecond = perSecond.isEmpty() ? 0.0
        : perSecond.stream().mapToLong(r -> ((Number) r.get("cnt")).longValue()).average().orElse(0.0);

    // Top N users
    List<Map<String, Object>> topUsers = new ArrayList<>();
    for (Map<String, Object> row : jdbc.queryForList(
        "SELECT user_id, username, COUNT(*) AS message_count FROM messages " +
        "GROUP BY user_id, username ORDER BY message_count DESC LIMIT ?", topN)) {
      Map<String, Object> m = new HashMap<>(row);
      topUsers.add(m);
    }

    // Top N rooms
    List<Map<String, Object>> topRooms = new ArrayList<>();
    for (Map<String, Object> row : jdbc.queryForList(
        "SELECT room_id, COUNT(*) AS message_count, COUNT(DISTINCT user_id) AS unique_users " +
        "FROM messages GROUP BY room_id ORDER BY message_count DESC LIMIT ?", topN)) {
      Map<String, Object> m = new HashMap<>(row);
      topRooms.add(m);
    }

    long ms = nanos(t0);
    AnalyticsSummary result = new AnalyticsSummary(
        total, avgPerSecond, avgPerSecond * 60, topUsers, topRooms, ms);
    cache.put(cacheKey, new CachedResult(result, System.currentTimeMillis() + CACHE_TTL_MS));
    return result;
  }

  private long nanos(long t0) { return (System.nanoTime() - t0) / 1_000_000; }

  private void warnIfSlow(String query, long ms, long targetMs) {
    if (ms > targetMs) {
      log.warn("SLOW QUERY: {} took {}ms (target <{}ms)", query, ms, targetMs);
    }
  }

  private record CachedResult(Object value, long expiresAt) {
    boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
  }
}
