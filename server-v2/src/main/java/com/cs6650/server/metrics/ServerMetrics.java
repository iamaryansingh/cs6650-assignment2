package com.cs6650.server.metrics;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class ServerMetrics {
  private final AtomicLong publishedOk = new AtomicLong();
  private final AtomicLong publishedFailed = new AtomicLong();
  private final AtomicLong broadcastSent = new AtomicLong();
  private final AtomicLong broadcastSkippedDuplicate = new AtomicLong();

  public void incPublishedOk() { publishedOk.incrementAndGet(); }
  public void incPublishedFailed() { publishedFailed.incrementAndGet(); }
  public void incBroadcastSent(long c) { broadcastSent.addAndGet(c); }
  public void incBroadcastSkippedDuplicate() { broadcastSkippedDuplicate.incrementAndGet(); }

  public long getPublishedOk() { return publishedOk.get(); }
  public long getPublishedFailed() { return publishedFailed.get(); }
  public long getBroadcastSent() { return broadcastSent.get(); }
  public long getBroadcastSkippedDuplicate() { return broadcastSkippedDuplicate.get(); }
}
