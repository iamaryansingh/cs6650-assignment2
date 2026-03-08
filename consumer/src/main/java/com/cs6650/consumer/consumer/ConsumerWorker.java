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

public class ConsumerWorker {
  private final int workerId;
  private final Connection connection;
  private final int prefetch;
  private final List<String> queues;
  private final BroadcastForwarder forwarder;
  private final ConsumerMetrics metrics;
  private final Map<String, Long> dedupeCache;
  private final long dedupeTtlMs;
  private final ObjectMapper objectMapper = new ObjectMapper();

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
          channel.basicNack(deliveryTag, false, true);
        }
      } catch (Exception e) {
        metrics.incFailed();
        channel.basicNack(deliveryTag, false, true);
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
    dedupeCache.entrySet().removeIf(e -> (now - e.getValue()) > dedupeTtlMs);
    return dedupeCache.putIfAbsent(workerId + ":" + messageId, now) == null;
  }
}
