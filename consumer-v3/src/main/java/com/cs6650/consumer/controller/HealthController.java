package com.cs6650.consumer.controller;

import com.cs6650.consumer.consumer.MultiThreadedConsumerManager;
import com.cs6650.consumer.metrics.ConsumerMetrics;
import com.cs6650.consumer.service.BatchMessageWriter;
import com.cs6650.consumer.service.DeadLetterService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final MultiThreadedConsumerManager manager;
  private final ConsumerMetrics metrics;
  private final BatchMessageWriter batchWriter;
  private final DeadLetterService deadLetterService;

  public HealthController(MultiThreadedConsumerManager manager, ConsumerMetrics metrics,
      BatchMessageWriter batchWriter, DeadLetterService deadLetterService) {
    this.manager = manager;
    this.metrics = metrics;
    this.batchWriter = batchWriter;
    this.deadLetterService = deadLetterService;
  }

  @GetMapping("/health")
  public ResponseEntity<Map<String, Object>> health() {
    return ResponseEntity.ok(Map.of(
        "status", manager.isRunning() ? "UP" : "STARTING",
        "workers", manager.workerCount(),
        "assignments", manager.workerAssignments(),
        "messagesProcessed", metrics.getMessagesProcessed(),
        "throughputMsgPerSec", metrics.getThroughputPerSecond(),
        "consumed", metrics.getConsumed(),
        "forwarded", metrics.getForwarded(),
        "failed", metrics.getFailed(),
        "duplicates", metrics.getDuplicates()));
  }

  @GetMapping("/metrics")
  public ResponseEntity<Map<String, Object>> consumerMetrics() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("messagesProcessed", metrics.getMessagesProcessed());
    m.put("throughputMsgPerSec", metrics.getThroughputPerSecond());
    m.put("consumed", metrics.getConsumed());
    m.put("forwarded", metrics.getForwarded());
    m.put("failed", metrics.getFailed());
    m.put("duplicates", metrics.getDuplicates());
    m.put("dbWritten", batchWriter.getTotalWritten());
    m.put("dbDuplicatesSkipped", batchWriter.getTotalDuplicatesSkipped());
    m.put("dbErrors", batchWriter.getTotalErrors());
    m.put("dbBufferSize", batchWriter.getBufferSize());
    m.put("deadLetterCount", deadLetterService.getDeadLetterCount());
    m.put("workers", manager.workerCount());
    return ResponseEntity.ok(m);
  }
}
