package com.cs6650.consumer.consumer;

import com.cs6650.consumer.config.ConsumerProperties;
import com.cs6650.consumer.config.RabbitMQProperties;
import com.cs6650.consumer.metrics.ConsumerMetrics;
import com.cs6650.consumer.service.BatchMessageWriter;
import com.cs6650.consumer.service.BroadcastForwarder;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class MultiThreadedConsumerManager {

  private final RabbitMQProperties rabbitMQProperties;
  private final ConsumerProperties consumerProperties;
  private final BroadcastForwarder forwarder;
  private final ConsumerMetrics metrics;
  private final BatchMessageWriter batchWriter;

  private Connection sharedConnection;
  private ExecutorService forwarderExecutor;
  private final List<ConsumerWorker> workers = new ArrayList<>();
  private final Map<String, Long> dedupeCache = new ConcurrentHashMap<>();
  private final Map<Integer, List<String>> workerQueues = new ConcurrentHashMap<>();
  private volatile boolean running;

  public MultiThreadedConsumerManager(
      RabbitMQProperties rabbitMQProperties,
      ConsumerProperties consumerProperties,
      BroadcastForwarder forwarder,
      ConsumerMetrics metrics,
      BatchMessageWriter batchWriter) {
    this.rabbitMQProperties = rabbitMQProperties;
    this.consumerProperties = consumerProperties;
    this.forwarder = forwarder;
    this.metrics = metrics;
    this.batchWriter = batchWriter;
  }

  @PostConstruct
  public void start() throws Exception {
    int workerCount = Math.max(1, consumerProperties.getWorkerCount());

    // Why 40-thread work pool on the AMQP connection:
    //   The RabbitMQ Java client dispatches deliver() callbacks from a shared work pool.
    //   Default pool size = max(availableProcessors, 4) = 4 threads on a t3.small (2 vCPUs).
    //   With 20 worker channels all sharing 4 threads, and each callback blocking on an
    //   HTTP POST (~3ms), throughput ceiling = 4 / 0.003 = 1,333 msg/s — exactly what
    //   we observed. Giving the connection 40 dispatch threads removes this ceiling.
    ExecutorService amqpWorkPool = Executors.newFixedThreadPool(40, namedFactory("amqp-worker"));

    // Separate pool for async broadcast forwarding so it never blocks the deliver callback.
    forwarderExecutor = Executors.newFixedThreadPool(20, namedFactory("broadcast"));

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitMQProperties.getHost());
    factory.setPort(rabbitMQProperties.getPort());
    factory.setUsername(rabbitMQProperties.getUsername());
    factory.setPassword(rabbitMQProperties.getPassword());
    factory.setVirtualHost(rabbitMQProperties.getVirtualHost());
    factory.setAutomaticRecoveryEnabled(true);
    factory.setNetworkRecoveryInterval(3000);

    // Pass the custom work pool so deliver callbacks dispatch from 40 threads, not 4.
    sharedConnection = factory.newConnection(amqpWorkPool, "consumer-pool");

    for (int idx = 0; idx < workerCount; idx++) {
      List<String> queues = assignedQueues(idx, workerCount);
      workerQueues.put(idx, List.copyOf(queues));
      ConsumerWorker worker = new ConsumerWorker(
          idx,
          sharedConnection,
          rabbitMQProperties.getPrefetch(),
          queues,
          forwarder,
          forwarderExecutor,
          metrics,
          dedupeCache,
          consumerProperties.getDedupeTtlMs(),
          batchWriter);

      worker.start();
      workers.add(worker);
    }
    running = true;
  }

  private List<String> assignedQueues(int workerIndex, int workerCount) {
    List<String> queues = new ArrayList<>();
    for (int room = 1; room <= rabbitMQProperties.getRoomCount(); room++) {
      if ((room - 1) % workerCount == workerIndex) {
        queues.add(rabbitMQProperties.getQueuePrefix() + room);
      }
    }
    return queues;
  }

  private static java.util.concurrent.ThreadFactory namedFactory(String prefix) {
    AtomicInteger counter = new AtomicInteger();
    return r -> {
      Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
  }

  @PreDestroy
  public void shutdown() {
    running = false;
    for (ConsumerWorker worker : workers) {
      worker.stop();
    }
    if (forwarderExecutor != null) {
      forwarderExecutor.shutdown();
    }
    if (sharedConnection != null) {
      try {
        sharedConnection.close();
      } catch (Exception ignored) {
      }
    }
  }

  public boolean isRunning() { return running; }
  public int workerCount()   { return workers.size(); }
  public Map<Integer, List<String>> workerAssignments() { return new HashMap<>(workerQueues); }
}
