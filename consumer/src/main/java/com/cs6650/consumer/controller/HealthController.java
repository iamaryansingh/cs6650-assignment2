package com.cs6650.consumer.controller;

import com.cs6650.consumer.consumer.MultiThreadedConsumerManager;
import com.cs6650.consumer.metrics.ConsumerMetrics;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final MultiThreadedConsumerManager manager;
  private final ConsumerMetrics metrics;

  public HealthController(MultiThreadedConsumerManager manager, ConsumerMetrics metrics) {
    this.manager = manager;
    this.metrics = metrics;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    return ResponseEntity.ok(Map.of(
        "status", manager.isRunning() ? "UP" : "STARTING",
        "workers", manager.workerCount(),
        "assignments", manager.workerAssignments(),
        "messagesProcessed", metrics.getMessagesProcessed(),
        "consumed", metrics.getConsumed(),
        "forwarded", metrics.getForwarded(),
        "failed", metrics.getFailed(),
        "duplicates", metrics.getDuplicates()));
  }

  @GetMapping("/metrics")
  public ResponseEntity<Map<String, Object>> metrics() {
    return ResponseEntity.ok(Map.of(
        "messagesProcessed", metrics.getMessagesProcessed(),
        "consumed", metrics.getConsumed(),
        "forwarded", metrics.getForwarded(),
        "failed", metrics.getFailed(),
        "duplicates", metrics.getDuplicates(),
        "workers", manager.workerCount()));
  }
}
