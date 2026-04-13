package com.cs6650.server.queue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.cs6650.server.config.RabbitMQProperties;

@Component
public class ChannelPool {

  private static final Logger log = LoggerFactory.getLogger(ChannelPool.class);

  /**
   * Why 3 connections instead of 1:
   * AMQP multiplexes channels over a single TCP connection. With 250 channels on one socket,
   * all publishes share the same TCP send/receive buffer and the broker's per-connection flow
   * control. Splitting across 3 connections gives 3 independent TCP sockets, each with its
   * own flow-control window, so a slow ACK burst on one connection doesn't stall the others.
   */
  private static final int CONNECTION_COUNT = 3;

  private final List<Connection> connections;
  private final BlockingQueue<PooledChannel> pool;

  public ChannelPool(RabbitMQProperties props) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(props.getHost());
    factory.setPort(props.getPort());
    factory.setUsername(props.getUsername());
    factory.setPassword(props.getPassword());
    factory.setVirtualHost(props.getVirtualHost());
    factory.setAutomaticRecoveryEnabled(true);
    factory.setNetworkRecoveryInterval(3000);

    connections = new ArrayList<>(CONNECTION_COUNT);
    for (int c = 0; c < CONNECTION_COUNT; c++) {
      connections.add(factory.newConnection("server-v2-producer-" + c));
    }
    log.info("Opened {} AMQP connections to RabbitMQ", CONNECTION_COUNT);

    int poolSize = props.getChannelPoolSize();
    this.pool = new ArrayBlockingQueue<>(poolSize);

    // Distribute channels evenly across connections: conn-0 gets channels 0,3,6,...
    // conn-1 gets 1,4,7,... conn-2 gets 2,5,8,...
    for (int i = 0; i < poolSize; i++) {
      Connection conn = connections.get(i % CONNECTION_COUNT);
      Channel ch = conn.createChannel();
      ch.confirmSelect();
      pool.add(new PooledChannel(ch));
    }
    log.info("ChannelPool initialized: {} channels across {} connections ({} channels/conn)",
        poolSize, CONNECTION_COUNT, poolSize / CONNECTION_COUNT);
  }

  public PooledChannel borrow() throws InterruptedException, IOException {
    PooledChannel pc = pool.take();
    if (!pc.isOpen()) {
      log.warn("Dead channel detected on borrow, creating replacement");
      return createReplacement();
    }
    return pc;
  }

  public void release(PooledChannel pc) {
    if (pc == null || !pc.isOpen()) {
      return;
    }
    pool.offer(pc);
  }

  private PooledChannel createReplacement() throws IOException {
    // Pick a random live connection for the replacement channel.
    Connection conn = connections.get(ThreadLocalRandom.current().nextInt(CONNECTION_COUNT));
    Channel ch = conn.createChannel();
    ch.confirmSelect();
    return new PooledChannel(ch);
  }

  @PreDestroy
  public void close() {
    for (PooledChannel pc : pool) {
      try {
        pc.channel().close();
      } catch (Exception ignored) {
      }
    }
    for (Connection conn : connections) {
      try {
        conn.close();
      } catch (IOException ignored) {
      }
    }
  }
}
