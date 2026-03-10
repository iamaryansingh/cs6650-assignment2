package com.cs6650.server.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BroadcastDedupeCache {
  private final Map<String, Long> seenMessageIds = new ConcurrentHashMap<>();
  private final long ttlMs;
  private volatile long lastCleanupMs = 0L;
  private static final long CLEANUP_INTERVAL_MS = 5000L;

  public BroadcastDedupeCache(@Value("${server.broadcast.dedupe-ttl-ms}") long ttlMs) {
    this.ttlMs = ttlMs;
  }

  public boolean firstTime(String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    long now = System.currentTimeMillis();
    if ((now - lastCleanupMs) >= CLEANUP_INTERVAL_MS) {
      cleanup(now);
      lastCleanupMs = now;
    }
    return seenMessageIds.putIfAbsent(messageId, now) == null;
  }

  private void cleanup(long now) {
    seenMessageIds.entrySet().removeIf(e -> (now - e.getValue()) > ttlMs);
  }
}
