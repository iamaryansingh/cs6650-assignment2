package com.cs6650.server.controller;

import com.cs6650.server.metrics.ServerMetrics;
import com.cs6650.server.model.ChatMessage;
import com.cs6650.server.service.BroadcastDedupeCache;
import com.cs6650.server.service.RoomSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalBroadcastController {
  private final RoomSessionRegistry roomSessionRegistry;
  private final BroadcastDedupeCache dedupeCache;
  private final ServerMetrics metrics;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public InternalBroadcastController(
      RoomSessionRegistry roomSessionRegistry,
      BroadcastDedupeCache dedupeCache,
      ServerMetrics metrics) {
    this.roomSessionRegistry = roomSessionRegistry;
    this.dedupeCache = dedupeCache;
    this.metrics = metrics;
  }

  @PostMapping("/broadcast")
  public ResponseEntity<Map<String, Object>> broadcast(@RequestBody ChatMessage message) throws Exception {
    if (!dedupeCache.firstTime(message.getMessageId())) {
      metrics.incBroadcastSkippedDuplicate();
      return ResponseEntity.ok(Map.of("status", "duplicate", "sent", 0));
    }

    int sent = roomSessionRegistry.broadcast(message.getRoomId(), objectMapper.writeValueAsString(message));
    metrics.incBroadcastSent(sent);

    Map<String, Object> response = new HashMap<>();
    response.put("status", "ok");
    response.put("sent", sent);
    return ResponseEntity.ok(response);
  }
}
