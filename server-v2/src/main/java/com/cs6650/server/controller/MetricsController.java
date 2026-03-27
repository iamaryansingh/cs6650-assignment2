package com.cs6650.server.controller;

import com.cs6650.server.metrics.MetricsService;
import com.cs6650.server.metrics.dto.ActiveUsersResult;
import com.cs6650.server.metrics.dto.AnalyticsSummary;
import com.cs6650.server.metrics.dto.RoomMessageResult;
import com.cs6650.server.metrics.dto.UserMessageResult;
import com.cs6650.server.metrics.dto.UserRoomsResult;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assignment 3 Part 1: Metrics API.
 *
 * Called by the client after the load test completes.
 * GET /api/metrics/summary — combined JSON for the screenshot.
 */
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

  private final MetricsService metricsService;

  public MetricsController(MetricsService metricsService) {
    this.metricsService = metricsService;
  }

  // ================================================================
  // CORE QUERY 1: Room messages in time range  (target < 100ms)
  // ================================================================
  @GetMapping("/messages/room/{roomId}")
  public ResponseEntity<RoomMessageResult> getRoomMessages(
      @PathVariable String roomId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
    return ResponseEntity.ok(metricsService.getRoomMessages(roomId, startTime, endTime));
  }

  // ================================================================
  // CORE QUERY 2: User message history  (target < 200ms)
  // ================================================================
  @GetMapping("/messages/user/{userId}")
  public ResponseEntity<UserMessageResult> getUserMessages(
      @PathVariable String userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
    return ResponseEntity.ok(metricsService.getUserMessages(userId, startTime, endTime));
  }

  // ================================================================
  // CORE QUERY 3: Count active users in time window  (target < 500ms)
  // ================================================================
  @GetMapping("/users/active")
  public ResponseEntity<ActiveUsersResult> getActiveUsers(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
    return ResponseEntity.ok(metricsService.getActiveUsers(startTime, endTime));
  }

  // ================================================================
  // CORE QUERY 4: Rooms user participated in  (target < 50ms)
  // ================================================================
  @GetMapping("/rooms/user/{userId}")
  public ResponseEntity<UserRoomsResult> getUserRooms(@PathVariable String userId) {
    return ResponseEntity.ok(metricsService.getUserRooms(userId));
  }

  // ================================================================
  // ANALYTICS: Summary (top users, top rooms, throughput)
  // ================================================================
  @GetMapping("/analytics/summary")
  public ResponseEntity<AnalyticsSummary> getAnalytics(
      @RequestParam(defaultValue = "10") int n) {
    return ResponseEntity.ok(metricsService.getAnalyticsSummary(n));
  }

  // ================================================================
  // COMBINED: Single endpoint for post-test client screenshot
  // GET /api/metrics/summary
  // ================================================================
  @GetMapping("/summary")
  public ResponseEntity<Map<String, Object>> getSummary() {
    // Use a sample room and user for the core query demonstrations
    Instant end = Instant.now();
    Instant start = end.minusSeconds(7200); // last 2 hours

    Map<String, Object> result = new LinkedHashMap<>();

    // Core queries
    result.put("coreQuery1_roomMessages_room1",
        metricsService.getRoomMessages("1", start, end));
    result.put("coreQuery2_userHistory_user0",
        metricsService.getUserMessages("user-0", start, end));
    result.put("coreQuery3_activeUsers",
        metricsService.getActiveUsers(start, end));
    result.put("coreQuery4_userRooms_user0",
        metricsService.getUserRooms("user-0"));

    // Analytics
    result.put("analytics",
        metricsService.getAnalyticsSummary(10));

    return ResponseEntity.ok(result);
  }
}
