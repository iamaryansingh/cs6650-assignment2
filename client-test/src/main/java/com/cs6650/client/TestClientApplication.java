package com.cs6650.client;

import com.cs6650.client.loadgen.LoadGenerator;
import com.cs6650.client.metrics.ClientMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class TestClientApplication {
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: java -jar chat-test-client.jar <ws-url> <threads> <total-messages> [metrics-base-url]");
      System.exit(1);
    }

    String wsUrl = args[0];
    int threads = Integer.parseInt(args[1]);
    int totalMessages = Integer.parseInt(args[2]);
    String metricsBaseUrl = args.length >= 4 ? args[3] : wsToHttpBase(wsUrl);

    System.out.println("Target endpoint: " + wsUrl);
    System.out.println("Threads: " + threads + ", Total messages: " + totalMessages);
    System.out.println("Metrics API base: " + metricsBaseUrl);

    ClientMetrics metrics = new ClientMetrics();
    LoadGenerator loadGenerator = new LoadGenerator(wsUrl, metrics);

    metrics.start();
    metrics.startPeriodicReporter(5);
    loadGenerator.run(threads, totalMessages);
    metrics.stopPeriodicReporter();
    metrics.end();

    System.out.println(metrics.report());

    // ============================================================
    // A3: Poll until consumer drains then print clean summary
    // ============================================================
    // Why poll instead of fixed sleep:
    //   Consumer at ~800 msg/s needs 500K/800 ≈ 625 seconds to drain, not 30.
    //   A fixed 30s wait means the metrics API sees only ~5% of messages in DB,
    //   making the throughput number meaningless. We poll until the DB count
    //   stops increasing (consumer idle = queue empty).
    waitForDrain(metricsBaseUrl, totalMessages);

    String metricsUrl = metricsBaseUrl + "/api/metrics/summary";
    System.out.println("Calling metrics API: " + metricsUrl);

    try {
      HttpClient httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(metricsUrl))
          .timeout(Duration.ofSeconds(60))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      String body = response.body();

      // Save raw JSON
      String filename = "metrics-results-" + System.currentTimeMillis() + ".json";
      Files.writeString(Path.of(filename), body);

      // Print clean formatted summary for screenshot
      System.out.println("\n╔══════════════════════════════════════════════╗");
      System.out.println("║         METRICS API RESULTS (A3)             ║");
      System.out.println("╚══════════════════════════════════════════════╝");

      ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
      JsonNode root = mapper.readTree(body);

      // Core Query 1
      JsonNode q1 = root.path("coreQuery1_roomMessages_room1");
      System.out.printf("%-35s %s%n", "Core Q1 - Room messages count:", q1.path("count").asText("N/A"));
      System.out.printf("%-35s %sms%n", "Core Q1 - Execution time:", q1.path("executionTimeMs").asText("N/A"));

      // Core Query 2
      JsonNode q2 = root.path("coreQuery2_userHistory_user0");
      System.out.printf("%-35s %s%n", "Core Q2 - User messages count:", q2.path("count").asText("N/A"));
      System.out.printf("%-35s %sms%n", "Core Q2 - Execution time:", q2.path("executionTimeMs").asText("N/A"));

      // Core Query 3
      JsonNode q3 = root.path("coreQuery3_activeUsers");
      System.out.printf("%-35s %s%n", "Core Q3 - Active users:", q3.path("activeUserCount").asText("N/A"));
      System.out.printf("%-35s %sms%n", "Core Q3 - Execution time:", q3.path("executionTimeMs").asText("N/A"));

      // Core Query 4
      JsonNode q4 = root.path("coreQuery4_userRooms_user0");
      System.out.printf("%-35s %s%n", "Core Q4 - Rooms participated:", q4.path("rooms").size());
      System.out.printf("%-35s %sms%n", "Core Q4 - Execution time:", q4.path("executionTimeMs").asText("N/A"));

      // Analytics
      JsonNode analytics = root.path("analytics");
      System.out.println("──────────────────────────────────────────────");
      System.out.printf("%-35s %s%n", "Total Messages in DB:", analytics.path("totalMessages").asText("N/A"));
      System.out.printf("%-35s %.2f%n", "Messages Per Second:", analytics.path("messagesPerSecond").asDouble(0));
      System.out.printf("%-35s %.2f%n", "Messages Per Minute:", analytics.path("messagesPerMinute").asDouble(0));
      System.out.printf("%-35s %sms%n", "Analytics Execution time:", analytics.path("executionTimeMs").asText("N/A"));

      // Top Users
      System.out.println("──────────────────────────────────────────────");
      System.out.println("Top Users:");
      JsonNode topUsers = analytics.path("topUsers");
      for (int i = 0; i < topUsers.size() && i < 5; i++) {
        JsonNode u = topUsers.get(i);
        System.out.printf("  %d. %s — %s messages%n",
            i + 1, u.path("user_id").asText(), u.path("message_count").asText());
      }

      // Top Rooms
      System.out.println("Top Rooms:");
      JsonNode topRooms = analytics.path("topRooms");
      for (int i = 0; i < topRooms.size() && i < 5; i++) {
        JsonNode r = topRooms.get(i);
        System.out.printf("  %d. Room %s — %s messages, %s users%n",
            i + 1, r.path("room_id").asText(), r.path("message_count").asText(),
            r.path("unique_users").asText());
      }

      System.out.println("╔══════════════════════════════════════════════╗");
      System.out.println("║  Raw JSON saved to: " + filename);
      System.out.println("╚══════════════════════════════════════════════╝");

    } catch (Exception e) {
      System.err.println("WARNING: Failed to fetch metrics API: " + e.getMessage());
      System.err.println("Manually call: curl " + metricsUrl);
    }
  }

  /**
   * Polls the metrics API every 30 seconds until total messages in DB stops increasing.
   * Two consecutive equal counts = consumer idle = queue drained.
   * Max wait: 20 minutes (generous ceiling for 500K msgs at ~800 msg/s = ~625s).
   */
  private static void waitForDrain(String metricsBaseUrl, int expectedMessages) {
    int pollIntervalSeconds = 30;
    int maxWaitSeconds = 1200; // 20 minutes
    int waited = 0;
    long prevCount = -1;
    int stableRounds = 0;

    System.out.println("\nPolling for queue drain (max " + (maxWaitSeconds / 60) + " min, poll every "
        + pollIntervalSeconds + "s)...");

    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    while (waited < maxWaitSeconds) {
      try {
        Thread.sleep(pollIntervalSeconds * 1000L);
        waited += pollIntervalSeconds;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }

      try {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(metricsBaseUrl + "/api/metrics/summary"))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonNode root = mapper.readTree(resp.body());
        long totalInDb = root.path("analytics").path("totalMessages").asLong(-1);
        double rate = root.path("analytics").path("messagesPerSecond").asDouble(-1);

        System.out.printf("[DRAIN] t=+%ds | DB rows: %,d / %,d | consumer: %.0f msg/s%n",
            waited, totalInDb, expectedMessages, rate);

        if (totalInDb == prevCount) {
          stableRounds++;
          if (stableRounds >= 2) {
            System.out.println("[DRAIN] Consumer idle — queue drained. Proceeding to final metrics.");
            break;
          }
        } else {
          stableRounds = 0;
        }
        prevCount = totalInDb;

      } catch (Exception e) {
        System.out.printf("[DRAIN] t=+%ds | metrics API unavailable: %s%n", waited, e.getMessage());
      }
    }

    if (waited >= maxWaitSeconds) {
      System.out.println("[DRAIN] Max wait reached. Some messages may still be in queue.");
    }
  }

  private static String wsToHttpBase(String wsUrl) {
    String url = wsUrl.trim();
    if (url.startsWith("wss://")) url = "https://" + url.substring(6);
    else if (url.startsWith("ws://")) url = "http://" + url.substring(5);
    URI uri = URI.create(url);
    int port = uri.getPort();
    return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
  }
}
