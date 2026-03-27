package com.cs6650.consumer.metrics;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class ConsumerMetrics {
  private final long startedAtMs = System.currentTimeMillis();
  private final AtomicLong messagesProcessed = new AtomicLong();
  private final AtomicLong consumed = new AtomicLong();
  private final AtomicLong forwarded = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong duplicates = new AtomicLong();
  private final AtomicLong duplicatesSkipped = new AtomicLong(); // A3: DB-level duplicates

  public void incConsumed() { consumed.incrementAndGet(); messagesProcessed.incrementAndGet(); }
  public void incForwarded() { forwarded.incrementAndGet(); }
  public void incFailed() { failed.incrementAndGet(); }
  public void incDuplicates() { duplicates.incrementAndGet(); }
  public void incDuplicatesSkipped() { duplicatesSkipped.incrementAndGet(); } // A3

  public long getMessagesProcessed() { return messagesProcessed.get(); }
  public long getConsumed() { return consumed.get(); }
  public long getForwarded() { return forwarded.get(); }
  public long getFailed() { return failed.get(); }
  public long getDuplicates() { return duplicates.get(); }
  public long getDuplicatesSkipped() { return duplicatesSkipped.get(); }

  public double getThroughputPerSecond() {
    long elapsedMs = Math.max(1, System.currentTimeMillis() - startedAtMs);
    return messagesProcessed.get() * 1000.0 / elapsedMs;
  }
}
