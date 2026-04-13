package com.cs6650.server.queue;

import com.cs6650.server.config.RabbitMQProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QueuePublisher {

  private static final Logger log = LoggerFactory.getLogger(QueuePublisher.class);

  private final ChannelPool channelPool;
  private final RabbitMQProperties props;

  public QueuePublisher(ChannelPool channelPool, RabbitMQProperties props) {
    this.channelPool = channelPool;
    this.props = props;
  }

  @PostConstruct
  public void initTopology() throws Exception {
    PooledChannel pc = channelPool.borrow();
    try {
      Channel ch = pc.channel();
      ch.exchangeDeclare(props.getExchange(), "topic", true);
      for (int i = 1; i <= props.getRoomCount(); i++) {
        String queueName = props.getQueuePrefix() + i;
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", props.getQueueMessageTtlMs());
        args.put("x-max-length", props.getQueueMaxLength());
        ch.queueDeclare(queueName, true, false, false, args);
        ch.queueBind(queueName, props.getExchange(), props.getQueuePrefix() + i);
      }
      log.info("RabbitMQ topology initialized for {} rooms", props.getRoomCount());
    } finally {
      channelPool.release(pc);
    }
  }

  /**
   * Publish a message to RabbitMQ without blocking for a per-message ACK.
   *
   * Previously this called waitForConfirmsOrDie() after every publish, which forced the
   * calling thread (a Tomcat WebSocket handler thread) to wait for RabbitMQ to fsync the
   * message before returning the channel to the pool. On a t3.small with EBS, that sync
   * takes 1–5ms, capping each channel at 200–1000 msg/s.
   *
   * Now: the publish completes immediately; RabbitMQ ACKs arrive asynchronously via
   * PooledChannel's confirm listener. The PooledChannel semaphore (MAX_OUTSTANDING=500)
   * ensures a channel blocks only if 500 unconfirmed messages pile up — which should not
   * happen under normal load and means throughput is no longer gated by ACK round-trips.
   */
  public void publish(String roomId, String payload) throws Exception {
    PooledChannel pc = channelPool.borrow();
    try {
      String routingKey = props.getQueuePrefix() + roomId;
      AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .build();
      pc.publish(props.getExchange(), routingKey, properties,
          payload.getBytes(StandardCharsets.UTF_8));
    } finally {
      channelPool.release(pc);
    }
  }
}
