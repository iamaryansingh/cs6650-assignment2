package com.cs6650.server.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BroadcastDedupeCache {
  private final Map<String, Long> seenMessageIds = new ConcurrentHashMap<>();
  private final long ttlMs;

  public BroadcastDedupeCache(@Value("${server.broadcast.dedupe-ttl-ms}") long ttlMs) {
    this.ttlMs = ttlMs;
  }

  public boolean firstTime(String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    long now = System.currentTimeMillis();
    cleanup(now);
    return seenMessageIds.putIfAbsent(messageId, now) == null;
  }

  private void cleanup(long now) {
    seenMessageIds.entrySet().removeIf(e -> (now - e.getValue()) > ttlMs);
  }
}
