package com.cs6650.server.controller;

import com.cs6650.server.metrics.ServerMetrics;
import com.cs6650.server.service.RoomSessionRegistry;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final RoomSessionRegistry registry;
  private final ServerMetrics metrics;
  private final String serverId;

  public HealthController(
      RoomSessionRegistry registry,
      ServerMetrics metrics,
      @Value("${server.instance-id}") String serverId) {
    this.registry = registry;
    this.metrics = metrics;
    this.serverId = serverId;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    return ResponseEntity.ok(Map.of(
        "status", "UP",
        "serverId", serverId,
        "rooms", registry.roomCount(),
        "sessions", registry.sessionCount(),
        "activeUsers", registry.activeUserCount(),
        "publishedOk", metrics.getPublishedOk(),
        "publishedFailed", metrics.getPublishedFailed(),
        "broadcastSent", metrics.getBroadcastSent(),
        "broadcastSkippedDuplicate", metrics.getBroadcastSkippedDuplicate()));
  }
}
