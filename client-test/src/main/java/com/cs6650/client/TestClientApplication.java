package com.cs6650.client;

import com.cs6650.client.loadgen.LoadGenerator;
import com.cs6650.client.metrics.ClientMetrics;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class TestClientApplication {
  public static void main(String[] args) throws Exception {
    // Usage: <ws-url> <threads> <total-messages> [metrics-http-url]
    // metrics-http-url defaults to http equivalent of ws-url host
    if (args.length < 3) {
      System.err.println("Usage: java -jar chat-test-client.jar <ws-url> <threads> <total-messages> [metrics-base-url]");
      System.err.println("Example: java -jar chat-test-client.jar ws://localhost:8080/ws/chat 256 500000 http://localhost:8080");
      System.exit(1);
    }

    String wsUrl = args[0];
    int threads = Integer.parseInt(args[1]);
    int totalMessages = Integer.parseInt(args[2]);
    // Optional 4th arg: metrics base URL (e.g. http://alb-host:8080)
    String metricsBaseUrl = args.length >= 4 ? args[3] : wsToHttpBase(wsUrl);

    System.out.println("Target endpoint: " + wsUrl);
    System.out.println("Threads: " + threads + ", Total messages: " + totalMessages);
    System.out.println("Metrics API base: " + metricsBaseUrl);

    ClientMetrics metrics = new ClientMetrics();
    LoadGenerator loadGenerator = new LoadGenerator(wsUrl, metrics);

    metrics.start();
    loadGenerator.run(threads, totalMessages);
    metrics.end();

    System.out.println(metrics.report());

    // ============================================================
    // A3: Call Metrics API after test completes
    // Wait for queue drain + DB batch flush before querying
    // ============================================================
    System.out.println("\nWaiting 15 seconds for queue drain and DB write completion...");
    Thread.sleep(15_000);

    String metricsUrl = metricsBaseUrl + "/api/metrics/summary";
    System.out.println("Calling metrics API: " + metricsUrl);

    try {
      HttpClient httpClient = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build();
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(metricsUrl))
          .timeout(Duration.ofSeconds(30))
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      System.out.println("\n========================================");
      System.out.println("         METRICS API RESULTS            ");
      System.out.println("HTTP Status: " + response.statusCode());
      System.out.println("========================================");
      System.out.println(response.body());
      System.out.println("========================================\n");

      // Save to file for report
      String filename = "metrics-results-" + System.currentTimeMillis() + ".json";
      Files.writeString(Path.of(filename), response.body());
      System.out.println("Metrics saved to: " + filename);

    } catch (Exception e) {
      System.err.println("WARNING: Failed to fetch metrics API: " + e.getMessage());
      System.err.println("You can manually call: curl " + metricsUrl);
    }
  }

  /** Convert ws://host:port/path -> http://host:port */
  private static String wsToHttpBase(String wsUrl) {
    String url = wsUrl.trim();
    if (url.startsWith("wss://")) {
      url = "https://" + url.substring(6);
    } else if (url.startsWith("ws://")) {
      url = "http://" + url.substring(5);
    }
    // Strip path
    URI uri = URI.create(url);
    int port = uri.getPort();
    return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
  }
}
