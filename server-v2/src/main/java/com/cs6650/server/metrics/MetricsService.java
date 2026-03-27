package com.cs6650.server.metrics;

import com.cs6650.server.metrics.dto.ActiveUsersResult;
import com.cs6650.server.metrics.dto.AnalyticsSummary;
import com.cs6650.server.metrics.dto.RoomActivity;
import com.cs6650.server.metrics.dto.RoomMessageResult;
import com.cs6650.server.metrics.dto.UserMessageResult;
import com.cs6650.server.metrics.dto.UserRoomsResult;
import com.cs6650.server.repository.MessageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment 3 Part 1: Metrics service for all core and analytics queries.
 * Simple in-memory cache with 5s TTL for analytics queries (Part 3).
 */
@Service
@Transactional(readOnly = true)
public class MetricsService {

  private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
  private static final long CACHE_TTL_MS = 5000;

  private final MessageRepository repository;
  private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

  public MetricsService(MessageRepository repository) {
    this.repository = repository;
  }

  // ================================================================
  // CORE QUERY 1: Room messages in time range  (target < 100ms)
  // ================================================================
  public RoomMessageResult getRoomMessages(String roomId, Instant start, Instant end) {
    long t0 = System.nanoTime();
    var messages = repository.findByRoomIdAndTimestampBetweenOrderByTimestampAsc(roomId, start, end);
    long ms = (System.nanoTime() - t0) / 1_000_000;
    warnIfSlow("getRoomMessages", ms, 100);
    return new RoomMessageResult(roomId, start.toString(), end.toString(), messages.size(), messages, ms);
  }

  // ================================================================
  // CORE QUERY 2: User message history  (target < 200ms)
  // ================================================================
  public UserMessageResult getUserMessages(String userId, Instant start, Instant end) {
    long t0 = System.nanoTime();
    var messages = (start != null && end != null)
        ? repository.findByUserIdAndTimestampBetweenOrderByTimestampDesc(userId, start, end)
        : repository.findByUserIdOrderByTimestampDesc(userId);
    long ms = (System.nanoTime() - t0) / 1_000_000;
    warnIfSlow("getUserMessages", ms, 200);
    return new UserMessageResult(userId, messages.size(), messages, ms);
  }

  // ================================================================
  // CORE QUERY 3: Count active users in time window  (target < 500ms)
  // ================================================================
  public ActiveUsersResult getActiveUsers(Instant start, Instant end) {
    long t0 = System.nanoTime();
    Long count = repository.countDistinctUsersByTimestampBetween(start, end);
    long ms = (System.nanoTime() - t0) / 1_000_000;
    warnIfSlow("getActiveUsers", ms, 500);
    return new ActiveUsersResult(count == null ? 0 : count, start.toString(), end.toString(), ms);
  }

  // ================================================================
  // CORE QUERY 4: Rooms a user participated in  (target < 50ms)
  // ================================================================
  public UserRoomsResult getUserRooms(String userId) {
    long t0 = System.nanoTime();
    List<Object[]> rows = repository.findRoomsWithActivityByUserId(userId);
    List<RoomActivity> rooms = new ArrayList<>();
    for (Object[] row : rows) {
      rooms.add(new RoomActivity(
          (String) row[0],
          row[1] == null ? null : row[1].toString(),
          row[2] == null ? 0L : ((Number) row[2]).longValue()));
    }
    long ms = (System.nanoTime() - t0) / 1_000_000;
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
    long total = repository.count();

    // Throughput from per-second bucketed counts
    List<Object[]> perSecond = repository.getMessagesPerSecond();
    double avgPerSecond = perSecond.isEmpty() ? 0.0
        : perSecond.stream().mapToLong(r -> ((Number) r[1]).longValue()).average().orElse(0.0);

    // Top users
    List<Map<String, Object>> topUsers = new ArrayList<>();
    for (Object[] row : repository.findTopUsers(topN)) {
      Map<String, Object> m = new HashMap<>();
      m.put("userId", row[0]);
      m.put("username", row[1]);
      m.put("messageCount", ((Number) row[2]).longValue());
      topUsers.add(m);
    }

    // Top rooms
    List<Map<String, Object>> topRooms = new ArrayList<>();
    for (Object[] row : repository.findTopRooms(topN)) {
      Map<String, Object> m = new HashMap<>();
      m.put("roomId", row[0]);
      m.put("messageCount", ((Number) row[1]).longValue());
      m.put("uniqueUsers", ((Number) row[2]).longValue());
      topRooms.add(m);
    }

    long ms = (System.nanoTime() - t0) / 1_000_000;
    AnalyticsSummary result = new AnalyticsSummary(
        total, avgPerSecond, avgPerSecond * 60, topUsers, topRooms, ms);

    cache.put(cacheKey, new CachedResult(result, System.currentTimeMillis() + CACHE_TTL_MS));
    return result;
  }

  private void warnIfSlow(String query, long ms, long targetMs) {
    if (ms > targetMs) {
      log.warn("SLOW QUERY: {} took {}ms (target <{}ms)", query, ms, targetMs);
    }
  }

  private record CachedResult(Object value, long expiresAt) {
    boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
  }
}
