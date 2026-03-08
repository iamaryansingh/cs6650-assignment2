package com.cs6650.client.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class ClientMetrics {
  private final AtomicLong sent = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private long startNanos;
  private long endNanos;

  public void start() { startNanos = System.nanoTime(); }
  public void end() { endNanos = System.nanoTime(); }
  public void sentOne() { sent.incrementAndGet(); }
  public void failOne() { failed.incrementAndGet(); }

  public String report() {
    double seconds = (endNanos - startNanos) / 1_000_000_000.0;
    long ok = sent.get();
    long ko = failed.get();
    double throughput = seconds > 0 ? ok / seconds : ok;
    double successRate = (ok + ko) > 0 ? (ok * 100.0) / (ok + ko) : 0.0;

    return "=== TEST RESULTS ===\n"
        + "Duration(s): " + String.format("%.2f", seconds) + "\n"
        + "Messages Sent: " + ok + "\n"
        + "Messages Failed: " + ko + "\n"
        + "Throughput(msg/s): " + String.format("%.2f", throughput) + "\n"
        + "Success Rate(%): " + String.format("%.2f", successRate) + "\n"
        + "====================";
  }
}
