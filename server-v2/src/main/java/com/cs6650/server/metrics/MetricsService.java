package com.cs6650.server.metrics;

import com.cs6650.server.metrics.dto.ActiveUsersResult;
import com.cs6650.server.metrics.dto.AnalyticsSummary;
import com.cs6650.server.metrics.dto.RoomActivity;
import com.cs6650.server.metrics.dto.RoomMessageResult;
import com.cs6650.server.metrics.dto.UserMessageResult;
import com.cs6650.server.metrics.dto.UserRoomsResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Optimization 2: Caffeine in-process cache for all metrics queries.
 *
 * Previously only getAnalyticsSummary was cached (5s TTL via ConcurrentHashMap).
 * The other 4 query methods hit PostgreSQL on every request — under load-test
 * conditions this adds unnecessary DB pressure and inflates latency.
 *
 * Cache TTLs chosen per query cost:
 *   - Room / user / active-users queries : 2 s  (cheap, but high call rate)
 *   - User rooms (GROUP BY aggregation)  : 3 s
 *   - Analytics summary (full table scan): 5 s
 *
 * Maximum cache sizes are generous relative to the 20-room / bounded-user
 * test workload so eviction never becomes a factor.
 */
@Service
public class MetricsService {

  private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

  private final JdbcTemplate jdbc;

  // Per-query caches with appropriate TTLs
  private final Cache<String, RoomMessageResult>  roomCache;
  private final Cache<String, UserMessageResult>  userCache;
  private final Cache<String, ActiveUsersResult>  activeUsersCache;
  private final Cache<String, UserRoomsResult>    userRoomsCache;
  private final Cache<String, AnalyticsSummary>   analyticsCache;

  public MetricsService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;

    this.roomCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofSeconds(2))
        .build();

    this.userCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofSeconds(2))
        .build();

    this.activeUsersCache = Caffeine.newBuilder()
        .maximumSize(200)
        .expireAfterWrite(Duration.ofSeconds(2))
        .build();

    this.userRoomsCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofSeconds(3))
        .build();

    this.analyticsCache = Caffeine.newBuilder()
        .maximumSize(50)
        .expireAfterWrite(Duration.ofSeconds(5))
        .build();
  }

  // Q1: room messages in time range
  public RoomMessageResult getRoomMessages(String roomId, Instant start, Instant end) {
    String key = "room:" + roomId + ":" + start + ":" + end;
    return roomCache.get(key, k -> queryRoomMessages(roomId, start, end));
  }

  private RoomMessageResult queryRoomMessages(String roomId, Instant start, Instant end) {
    long t0 = System.nanoTime();
    Long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM messages WHERE room_id = ? AND timestamp BETWEEN ? AND ?",
        Long.class, roomId, Timestamp.from(start), Timestamp.from(end));
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT message_id, room_id, user_id, username, message, timestamp, message_type " +
        "FROM messages WHERE room_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC LIMIT 5",
        roomId, Timestamp.from(start), Timestamp.from(end));
    long ms = nanos(t0);
    warnIfSlow("getRoomMessages", ms, 100);
    return new RoomMessageResult(roomId, start.toString(), end.toString(),
        total == null ? 0 : total.intValue(), rows, ms);
  }

  // Q2: user message history
  public UserMessageResult getUserMessages(String userId, Instant start, Instant end) {
    String key = "user:" + userId + ":" + start + ":" + end;
    return userCache.get(key, k -> queryUserMessages(userId, start, end));
  }

  private UserMessageResult queryUserMessages(String userId, Instant start, Instant end) {
    long t0 = System.nanoTime();
    List<Map<String, Object>> rows;
    Long total;
    if (start != null && end != null) {
      total = jdbc.queryForObject(
          "SELECT COUNT(*) FROM messages WHERE user_id = ? AND timestamp BETWEEN ? AND ?",
          Long.class, userId, Timestamp.from(start), Timestamp.from(end));
      rows = jdbc.queryForList(
          "SELECT message_id, room_id, user_id, username, message, timestamp, message_type " +
          "FROM messages WHERE user_id = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp DESC LIMIT 5",
          userId, Timestamp.from(start), Timestamp.from(end));
    } else {
      total = jdbc.queryForObject(
          "SELECT COUNT(*) FROM messages WHERE user_id = ?", Long.class, userId);
      rows = jdbc.queryForList(
          "SELECT message_id, room_id, user_id, username, message, timestamp, message_type " +
          "FROM messages WHERE user_id = ? ORDER BY timestamp DESC LIMIT 5",
          userId);
    }
    long ms = nanos(t0);
    warnIfSlow("getUserMessages", ms, 200);
    return new UserMessageResult(userId, total == null ? 0 : total.intValue(), rows, ms);
  }

  // Q3: distinct active users in time window
  public ActiveUsersResult getActiveUsers(Instant start, Instant end) {
    String key = "active:" + start + ":" + end;
    return activeUsersCache.get(key, k -> queryActiveUsers(start, end));
  }

  private ActiveUsersResult queryActiveUsers(Instant start, Instant end) {
    long t0 = System.nanoTime();
    Long count = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT user_id) FROM messages WHERE timestamp BETWEEN ? AND ?",
        Long.class, Timestamp.from(start), Timestamp.from(end));
    long ms = nanos(t0);
    warnIfSlow("getActiveUsers", ms, 500);
    return new ActiveUsersResult(count == null ? 0 : count, start.toString(), end.toString(), ms);
  }

  // Q4: rooms a user has participated in
  public UserRoomsResult getUserRooms(String userId) {
    return userRoomsCache.get(userId, this::queryUserRooms);
  }

  private UserRoomsResult queryUserRooms(String userId) {
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

  // Q5: analytics summary
  public AnalyticsSummary getAnalyticsSummary(int topN) {
    String key = "analytics:" + topN;
    return analyticsCache.get(key, k -> queryAnalyticsSummary(topN));
  }

  private AnalyticsSummary queryAnalyticsSummary(int topN) {
    long t0 = System.nanoTime();

    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM messages", Long.class);
    if (total == null) total = 0L;

    Map<String, Object> timeRange = jdbc.queryForMap(
        "SELECT COUNT(*) AS cnt, " +
        "EXTRACT(EPOCH FROM (MAX(timestamp) - MIN(timestamp))) AS elapsed_sec FROM messages");
    long totalCount = timeRange.get("cnt") == null ? 0 : ((Number) timeRange.get("cnt")).longValue();
    double elapsedSec = timeRange.get("elapsed_sec") == null ? 1.0 :
        Math.max(1.0, ((Number) timeRange.get("elapsed_sec")).doubleValue());
    double avgPerSecond = totalCount / elapsedSec;

    List<Map<String, Object>> topUsers = new ArrayList<>();
    for (Map<String, Object> row : jdbc.queryForList(
        "SELECT user_id, username, COUNT(*) AS message_count FROM messages " +
        "GROUP BY user_id, username ORDER BY message_count DESC LIMIT ?", topN)) {
      topUsers.add(new HashMap<>(row));
    }

    List<Map<String, Object>> topRooms = new ArrayList<>();
    for (Map<String, Object> row : jdbc.queryForList(
        "SELECT room_id, COUNT(*) AS message_count, COUNT(DISTINCT user_id) AS unique_users " +
        "FROM messages GROUP BY room_id ORDER BY message_count DESC LIMIT ?", topN)) {
      topRooms.add(new HashMap<>(row));
    }

    long ms = nanos(t0);
    return new AnalyticsSummary(total, avgPerSecond, avgPerSecond * 60, topUsers, topRooms, ms);
  }

  private long nanos(long t0) { return (System.nanoTime() - t0) / 1_000_000; }

  private void warnIfSlow(String query, long ms, long targetMs) {
    if (ms > targetMs) {
      log.warn("SLOW QUERY: {} took {}ms (target <{}ms)", query, ms, targetMs);
    }
  }
}
