package com.cs6650.client.loadgen;

import com.cs6650.client.metrics.ClientMetrics;
import com.cs6650.client.model.ChatMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadGenerator {
  private final URI wsUri;
  private final ClientMetrics metrics;

  public LoadGenerator(String wsUrl, ClientMetrics metrics) {
    this.wsUri = normalizeWsUri(wsUrl);
    this.metrics = metrics;
  }

  public void run(int threads, int totalMessages) {
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    int perThread = totalMessages / threads;
    int remainder = totalMessages % threads;

    for (int t = 0; t < threads; t++) {
      int count = perThread + (t < remainder ? 1 : 0);
      int threadId = t;
      executor.submit(() -> send(threadId, count));
    }

    executor.shutdown();
    try {
      executor.awaitTermination(2, TimeUnit.HOURS);
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private void send(int threadId, int count) {
    HttpClient client = HttpClient.newHttpClient();
    CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
        .buildAsync(wsUri, new WebSocket.Listener() {});

    WebSocket ws;
    try {
      ws = wsFuture.join();
    } catch (Exception e) {
      metrics.connectFail();
      metrics.sampleError(rootMessage(e));
      for (int i = 0; i < count; i++) {
        metrics.failOne();
      }
      return;
    }

    for (int i = 0; i < count; i++) {
      ChatMessage msg = new ChatMessage();
      msg.userId = "user-" + threadId;
      msg.username = "User" + threadId;
      msg.roomId = Integer.toString((i % 20) + 1);
      msg.message = "load message " + i;

      try {
        ws.sendText(msg.toJson(), true).join();
        metrics.sentOne(msg.roomId, msg.messageType);
      } catch (Exception e) {
        metrics.failOne();
        metrics.sampleError(rootMessage(e));
      }
    }

    try {
      ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    } catch (Exception ignored) {
    }
  }

  private static URI normalizeWsUri(String input) {
    String value = input.trim();

    if (!value.contains("://")) {
      value = "ws://" + value;
    }
    if (value.startsWith("http://")) {
      value = "ws://" + value.substring("http://".length());
    } else if (value.startsWith("https://")) {
      value = "wss://" + value.substring("https://".length());
    }

    URI uri = URI.create(value);
    String path = uri.getPath();
    if (path == null || path.isBlank() || "/".equals(path)) {
      return URI.create(value + (value.endsWith("/") ? "ws/chat" : "/ws/chat"));
    }
    return uri;
  }

  private static String rootMessage(Throwable throwable) {
    Throwable t = throwable;
    while (t.getCause() != null) {
      t = t.getCause();
    }
    return t.getClass().getSimpleName() + ": " + (t.getMessage() == null ? "no message" : t.getMessage());
  }
}
