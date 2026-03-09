package com.cs6650.client.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ClientMetrics {
  private final AtomicLong sent = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong connectFailed = new AtomicLong();
  private final AtomicReference<String> sampleError = new AtomicReference<>();
  private long startNanos;
  private long endNanos;

  public void start() { startNanos = System.nanoTime(); }
  public void end() { endNanos = System.nanoTime(); }
  public void sentOne() { sent.incrementAndGet(); }
  public void failOne() { failed.incrementAndGet(); }
  public void connectFail() { connectFailed.incrementAndGet(); }
  public void sampleError(String error) {
    if (error == null || error.isBlank()) {
      return;
    }
    sampleError.compareAndSet(null, error);
  }

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
        + "Connection Failures: " + connectFailed.get() + "\n"
        + "Throughput(msg/s): " + String.format("%.2f", throughput) + "\n"
        + "Success Rate(%): " + String.format("%.2f", successRate) + "\n"
        + "Sample Error: " + (sampleError.get() == null ? "none" : sampleError.get()) + "\n"
        + "====================";
  }
}
