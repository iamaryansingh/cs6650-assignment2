package com.cs6650.client.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.TreeMap;

public class ClientMetrics {
  private final AtomicLong sent = new AtomicLong();
  private final AtomicLong failed = new AtomicLong();
  private final AtomicLong connectFailed = new AtomicLong();
  private final AtomicReference<String> sampleError = new AtomicReference<>();
  private final ConcurrentHashMap<String, AtomicLong> roomCounts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> typeCounts = new ConcurrentHashMap<>();
  private long startNanos;
  private long endNanos;

  // Live throughput reporter
  private volatile boolean reporterRunning = false;
  private Thread reporterThread;
  private final AtomicLong lastSentSnapshot = new AtomicLong(0);

  public void start() { startNanos = System.nanoTime(); }
  public void end()   { endNanos   = System.nanoTime(); }

  public void startPeriodicReporter(int intervalSeconds) {
    reporterRunning = true;
    lastSentSnapshot.set(0);
    reporterThread = new Thread(() -> {
      while (reporterRunning) {
        try {
          Thread.sleep(intervalSeconds * 1000L);
        } catch (InterruptedException e) {
          break;
        }
        long currentSent = sent.get();
        long prev        = lastSentSnapshot.getAndSet(currentSent);
        long delta       = currentSent - prev;
        double rate      = delta / (double) intervalSeconds;
        double elapsed   = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        System.out.printf("[CLIENT] t=%5.0fs | window=%,8.0f msg/s | total sent=%,d | failed=%,d%n",
            elapsed, rate, currentSent, failed.get());
      }
    });
    reporterThread.setDaemon(true);
    reporterThread.setName("client-metrics-reporter");
    reporterThread.start();
  }

  public void stopPeriodicReporter() {
    reporterRunning = false;
    if (reporterThread != null) {
      reporterThread.interrupt();
    }
  }
  public void sentOne() { sent.incrementAndGet(); }
  public void sentOne(String roomId, String messageType) {
    sent.incrementAndGet();
    if (roomId != null && !roomId.isBlank()) {
      roomCounts.computeIfAbsent(roomId, k -> new AtomicLong()).incrementAndGet();
    }
    String type = (messageType == null || messageType.isBlank()) ? "TEXT" : messageType.toUpperCase();
    typeCounts.computeIfAbsent(type, k -> new AtomicLong()).incrementAndGet();
  }
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
        + formatRoomThroughput(seconds)
        + formatMessageTypeDistribution(ok)
        + "====================";
  }

  private String formatRoomThroughput(double seconds) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n=== THROUGHPUT PER ROOM ===\n");

    Map<String, AtomicLong> sorted = new TreeMap<>((a, b) -> Integer.compare(parseRoom(a), parseRoom(b)));
    sorted.putAll(roomCounts);

    for (Map.Entry<String, AtomicLong> entry : sorted.entrySet()) {
      long count = entry.getValue().get();
      double roomRate = seconds > 0 ? count / seconds : count;
      sb.append("Room ").append(entry.getKey()).append(": ")
          .append(count).append(" messages, ")
          .append(String.format("%.2f", roomRate)).append(" msg/sec\n");
    }
    return sb.toString();
  }

  private String formatMessageTypeDistribution(long totalSent) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n=== MESSAGE TYPE DISTRIBUTION ===\n");

    Map<String, AtomicLong> sorted = new TreeMap<>(typeCounts);
    for (Map.Entry<String, AtomicLong> entry : sorted.entrySet()) {
      long count = entry.getValue().get();
      double pct = totalSent > 0 ? (count * 100.0) / totalSent : 0.0;
      sb.append(entry.getKey()).append(": ")
          .append(count).append(" (")
          .append(String.format("%.1f", pct)).append("%)\n");
    }
    return sb.toString();
  }

  private int parseRoom(String room) {
    try {
      return Integer.parseInt(room);
    } catch (NumberFormatException e) {
      return Integer.MAX_VALUE;
    }
  }
}
