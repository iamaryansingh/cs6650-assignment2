package com.cs6650.server.metrics;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ServerMetrics {

  private static final Logger log = LoggerFactory.getLogger(ServerMetrics.class);

  private final AtomicLong publishedOk = new AtomicLong();
  private final AtomicLong publishedFailed = new AtomicLong();
  private final AtomicLong broadcastSent = new AtomicLong();
  private final AtomicLong broadcastSkippedDuplicate = new AtomicLong();

  // Snapshot for per-interval rate calculation
  private final AtomicLong lastPublishedOkSnapshot = new AtomicLong(0);
  private volatile long lastSnapshotMs = System.currentTimeMillis();

  public void incPublishedOk() { publishedOk.incrementAndGet(); }
  public void incPublishedFailed() { publishedFailed.incrementAndGet(); }
  public void incBroadcastSent(long c) { broadcastSent.addAndGet(c); }
  public void incBroadcastSkippedDuplicate() { broadcastSkippedDuplicate.incrementAndGet(); }

  public long getPublishedOk() { return publishedOk.get(); }
  public long getPublishedFailed() { return publishedFailed.get(); }
  public long getBroadcastSent() { return broadcastSent.get(); }
  public long getBroadcastSkippedDuplicate() { return broadcastSkippedDuplicate.get(); }

  @Scheduled(fixedRate = 5000)
  public void logThroughput() {
    long nowMs       = System.currentTimeMillis();
    long current     = publishedOk.get();
    long prev        = lastPublishedOkSnapshot.getAndSet(current);
    double elapsedSec = Math.max(1, nowMs - lastSnapshotMs) / 1000.0;
    lastSnapshotMs   = nowMs;
    double rate      = (current - prev) / elapsedSec;
    log.info("[SERVER-PRODUCER] window={} msg/s | total published={} | failed={}",
        String.format("%,.0f", rate), current, publishedFailed.get());
  }
}
