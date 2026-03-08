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
    Channel ch = channelPool.borrow();
    try {
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
      channelPool.release(ch);
    }
  }

  public void publish(String roomId, String payload) throws Exception {
    Channel ch = channelPool.borrow();
    try {
      String routingKey = props.getQueuePrefix() + roomId;
      AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .build();
      ch.basicPublish(props.getExchange(), routingKey, true, properties, payload.getBytes(StandardCharsets.UTF_8));
      ch.waitForConfirmsOrDie(props.getPublisherTimeoutMs());
    } finally {
      channelPool.release(ch);
    }
  }
}
