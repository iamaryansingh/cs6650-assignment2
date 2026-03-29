package com.cs6650.server.controller;

import com.cs6650.server.metrics.MetricsService;
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

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

  private final MetricsService metricsService;

  public MetricsController(MetricsService metricsService) {
    this.metricsService = metricsService;
  }

  @GetMapping("/messages/room/{roomId}")
  public ResponseEntity<?> getRoomMessages(
      @PathVariable String roomId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
    return ResponseEntity.ok(metricsService.getRoomMessages(roomId, startTime, endTime));
  }

  @GetMapping("/messages/user/{userId}")
  public ResponseEntity<?> getUserMessages(
      @PathVariable String userId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
    return ResponseEntity.ok(metricsService.getUserMessages(userId, startTime, endTime));
  }

  @GetMapping("/users/active")
  public ResponseEntity<?> getActiveUsers(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
    return ResponseEntity.ok(metricsService.getActiveUsers(startTime, endTime));
  }

  @GetMapping("/rooms/user/{userId}")
  public ResponseEntity<?> getUserRooms(@PathVariable String userId) {
    return ResponseEntity.ok(metricsService.getUserRooms(userId));
  }

  @GetMapping("/analytics/summary")
  public ResponseEntity<?> getAnalytics(@RequestParam(defaultValue = "10") int n) {
    return ResponseEntity.ok(metricsService.getAnalyticsSummary(n));
  }

  /**
   * Combined endpoint called by client after load test.
   * Each query is wrapped independently — one failure won't break the whole response.
   */
  @GetMapping("/summary")
  public ResponseEntity<Map<String, Object>> getSummary() {
    Instant end = Instant.now();
    Instant start = end.minusSeconds(7200);

    Map<String, Object> result = new LinkedHashMap<>();

    result.put("coreQuery1_roomMessages_room1", safeGet(() ->
        metricsService.getRoomMessages("1", start, end)));

    result.put("coreQuery2_userHistory_user0", safeGet(() ->
        metricsService.getUserMessages("user-0", start, end)));

    result.put("coreQuery3_activeUsers", safeGet(() ->
        metricsService.getActiveUsers(start, end)));

    result.put("coreQuery4_userRooms_user0", safeGet(() ->
        metricsService.getUserRooms("user-0")));

    result.put("analytics", safeGet(() ->
        metricsService.getAnalyticsSummary(10)));

    return ResponseEntity.ok(result);
  }

  private Object safeGet(java.util.concurrent.Callable<Object> supplier) {
    try {
      return supplier.call();
    } catch (Exception e) {
      Map<String, String> err = new LinkedHashMap<>();
      err.put("error", e.getMessage());
      return err;
    }
  }
}
