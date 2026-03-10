package com.cs6650.consumer.consumer;

import com.cs6650.consumer.metrics.ConsumerMetrics;
import com.cs6650.consumer.model.ChatMessage;
import com.cs6650.consumer.service.BroadcastForwarder;
import com.cs6650.consumer.service.ForwardResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerWorker {
  private static final Logger log = LoggerFactory.getLogger(ConsumerWorker.class);

  private final int workerId;
  private final Connection connection;
  private final int prefetch;
  private final List<String> queues;
  private final BroadcastForwarder forwarder;
  private final ConsumerMetrics metrics;
  private final Map<String, Long> dedupeCache;
  private final long dedupeTtlMs;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private volatile long lastDedupeCleanupMs = 0L;
  private static final long DEDUPE_CLEANUP_INTERVAL_MS = 5000L;

  private Channel channel;
  private final List<String> consumerTags = new ArrayList<>();

  public ConsumerWorker(
      int workerId,
      Connection connection,
      int prefetch,
      List<String> queues,
      BroadcastForwarder forwarder,
      ConsumerMetrics metrics,
      Map<String, Long> dedupeCache,
      long dedupeTtlMs) {
    this.workerId = workerId;
    this.connection = connection;
    this.prefetch = prefetch;
    this.queues = queues;
    this.forwarder = forwarder;
    this.metrics = metrics;
    this.dedupeCache = dedupeCache;
    this.dedupeTtlMs = dedupeTtlMs;
  }

  public void start() throws Exception {
    channel = connection.createChannel();
    channel.basicQos(prefetch);

    DeliverCallback deliver = (tag, delivery) -> {
      metrics.incConsumed();
      String payload = new String(delivery.getBody(), StandardCharsets.UTF_8);
      long deliveryTag = delivery.getEnvelope().getDeliveryTag();

      try {
        ChatMessage message = objectMapper.readValue(payload, ChatMessage.class);

        if (!firstTime(message.getMessageId())) {
          metrics.incDuplicates();
          channel.basicAck(deliveryTag, false);
          return;
        }

        ForwardResult result = forwarder.forwardToAll(message);
        if (result.success()) {
          metrics.incForwarded();
          channel.basicAck(deliveryTag, false);
        } else {
          metrics.incFailed();
          // Forwarder already retries at HTTP layer. Requeue false prevents poison-message loops.
          channel.basicNack(deliveryTag, false, false);
          log.warn("Worker {} failed to forward messageId={} roomId={} after retries; dropped",
              workerId, message.getMessageId(), message.getRoomId());
        }
      } catch (Exception e) {
        metrics.incFailed();
        channel.basicNack(deliveryTag, false, false);
        log.warn("Worker {} failed to process deliveryTag={} payloadLength={} error={}",
            workerId, deliveryTag, payload.length(), e.toString());
      }
    };

    CancelCallback cancel = tag -> { };

    for (String queue : queues) {
      String consumerTag = channel.basicConsume(queue, false, deliver, cancel);
      consumerTags.add(consumerTag);
    }
  }

  public void stop() {
    if (channel == null) {
      return;
    }
    try {
      for (String tag : consumerTags) {
        channel.basicCancel(tag);
      }
      channel.close();
    } catch (Exception ignored) {
    }
  }

  private boolean firstTime(String messageId) {
    if (messageId == null || messageId.isBlank()) {
      return true;
    }
    long now = System.currentTimeMillis();
    if ((now - lastDedupeCleanupMs) >= DEDUPE_CLEANUP_INTERVAL_MS) {
      dedupeCache.entrySet().removeIf(e -> (now - e.getValue()) > dedupeTtlMs);
      lastDedupeCleanupMs = now;
    }
    return dedupeCache.putIfAbsent(workerId + ":" + messageId, now) == null;
  }
}
