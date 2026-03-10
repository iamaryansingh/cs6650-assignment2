package com.cs6650.consumer.service;

import com.cs6650.consumer.config.ConsumerProperties;
import com.cs6650.consumer.model.ChatMessage;
import com.cs6650.consumer.util.RetryExecutor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class BroadcastForwarder {
  private static final Logger log = LoggerFactory.getLogger(BroadcastForwarder.class);

  private final ConsumerProperties properties;
  private final HttpClient client;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public BroadcastForwarder(ConsumerProperties properties) {
    this.properties = properties;
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
        .build();
  }

  public ForwardResult forwardToAll(ChatMessage message) {
    List<String> endpoints = properties.endpointList();
    String body;
    try {
      body = objectMapper.writeValueAsString(message);
    } catch (Exception e) {
      log.error("Failed to serialize message {}: {}", message.getMessageId(), e.getMessage());
      return new ForwardResult(false, endpoints.size());
    }

    for (String endpoint : endpoints) {
      String url = endpoint + "/internal/broadcast";
      if (!postWithRetry(url, body, properties.getMaxRetries())) {
        log.warn("Forward failed for message {} to endpoint {}", message.getMessageId(), url);
        return new ForwardResult(false, endpoints.size());
      }
    }
    return new ForwardResult(true, endpoints.size());
  }

  private boolean postWithRetry(String url, String body, int maxRetries) {
    return RetryExecutor.run(() -> {
      try {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(properties.getRequestTimeoutMs()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          return 1;
        }
        log.warn("Broadcast POST non-2xx. url={} status={} body={}",
            url, response.statusCode(), response.body());
      } catch (Exception ignored) {
        log.warn("Broadcast POST exception. url={} error={}", url, ignored.toString());
      }
      return 0;
    }, maxRetries);
  }
}
