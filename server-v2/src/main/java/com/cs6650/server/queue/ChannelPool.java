package com.cs6650.server.queue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.cs6650.server.config.RabbitMQProperties;

@Component
public class ChannelPool {
  private static final Logger log = LoggerFactory.getLogger(ChannelPool.class);

  private final Connection connection;
  private final BlockingQueue<Channel> pool;

  public ChannelPool(RabbitMQProperties props) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(props.getHost());
    factory.setPort(props.getPort());
    factory.setUsername(props.getUsername());
    factory.setPassword(props.getPassword());
    factory.setVirtualHost(props.getVirtualHost());
    factory.setAutomaticRecoveryEnabled(true);
    factory.setNetworkRecoveryInterval(3000);

    this.connection = factory.newConnection("cs6650-server");
    this.pool = new ArrayBlockingQueue<>(props.getChannelPoolSize());

    for (int i = 0; i < props.getChannelPoolSize(); i++) {
      Channel ch = connection.createChannel();
      ch.confirmSelect();
      pool.add(ch);
    }
    log.info("ChannelPool initialized with {} channels", props.getChannelPoolSize());
  }

  public Channel borrow() throws InterruptedException, IOException {
    Channel ch = pool.take();
    if (!ch.isOpen()) {
      return connection.createChannel();
    }
    return ch;
  }

  public void release(Channel ch) {
    if (ch == null) {
      return;
    }
    if (!ch.isOpen()) {
      return;
    }
    pool.offer(ch);
  }

  @PreDestroy
  public void close() {
    for (Channel ch : pool) {
      try {
        ch.close();
      } catch (IOException | TimeoutException ignored) {
      }
    }
    try {
      connection.close();
    } catch (IOException ignored) {
    }
  }
}
