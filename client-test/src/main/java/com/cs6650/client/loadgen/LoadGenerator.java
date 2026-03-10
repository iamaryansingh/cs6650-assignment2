package com.cs6650.client.loadgen;

import com.cs6650.client.metrics.ClientMetrics;
import com.cs6650.client.model.ChatMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class LoadGenerator {
  private static final int TEXT_PERCENT = 90;
  private static final int JOIN_PERCENT = 5;
  private static final int LEAVE_PERCENT = 5;
  private static final int SEND_RETRY_LIMIT = 2;
  private static final long SEND_TIMEOUT_SECONDS = 10L;

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
    WebSocket ws = connect(client);
    if (ws == null) {
      metrics.connectFail();
      metrics.sampleError("Failed to establish initial websocket connection");
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
      msg.messageType = chooseMessageType();
      msg.message = buildMessageBody(msg.messageType, i);
      String payload;
      try {
        payload = msg.toJson();
      } catch (Exception e) {
        metrics.failOne();
        metrics.sampleError(rootMessage(e));
        continue;
      }

      boolean sent = false;
      for (int attempt = 1; attempt <= SEND_RETRY_LIMIT && !sent; attempt++) {
        try {
          ws.sendText(payload, true).orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
          metrics.sentOne(msg.roomId, msg.messageType);
          sent = true;
        } catch (CompletionException e) {
          metrics.sampleError(rootMessage(e));
          if (attempt < SEND_RETRY_LIMIT) {
            try {
              ws.abort();
            } catch (Exception ignored) {
            }
            ws = connect(client);
            if (ws == null) {
              metrics.connectFail();
              break;
            }
          }
        }
      }
      if (!sent) {
        metrics.failOne();
      }

      if ((i + 1) % 10000 == 0) {
        System.out.println("Thread-" + threadId + " progress: " + (i + 1) + "/" + count);
      }
    }

    try {
      ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
    } catch (Exception ignored) {
    }
  }

  private WebSocket connect(HttpClient client) {
    try {
      return client.newWebSocketBuilder()
          .connectTimeout(java.time.Duration.ofSeconds(5))
          .buildAsync(wsUri, new WebSocket.Listener() {})
          .join();
    } catch (Exception e) {
      metrics.sampleError(rootMessage(e));
      return null;
    }
  }

  private static String chooseMessageType() {
    int roll = ThreadLocalRandom.current().nextInt(100);
    if (roll < TEXT_PERCENT) {
      return "TEXT";
    }
    if (roll < TEXT_PERCENT + JOIN_PERCENT) {
      return "JOIN";
    }
    return "LEAVE";
  }

  private static String buildMessageBody(String type, int index) {
    return switch (type) {
      case "JOIN" -> "user joined room #" + index;
      case "LEAVE" -> "user left room #" + index;
      default -> "load text message " + index;
    };
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
