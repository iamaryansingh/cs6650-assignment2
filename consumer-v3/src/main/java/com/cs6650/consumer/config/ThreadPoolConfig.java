package com.cs6650.consumer.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Assignment 3 Part 2: Three separate thread pools as required.
 *  1. consumerThreadPool   - RabbitMQ message consumption
 *  2. dbWriterThreadPool   - Database batch writes
 *  3. statsThreadPool      - Statistics aggregation
 */
@Configuration
public class ThreadPoolConfig {

  /** Pool for RabbitMQ consumer workers. Sized to handle high ingest. */
  @Bean(name = "consumerThreadPool", destroyMethod = "shutdown")
  public ExecutorService consumerThreadPool() {
    return new ThreadPoolExecutor(
        10, 20,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(1000),
        namedFactory("consumer"),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /** Pool for async batch database writes.
   *  Increased from 5-10 to 10-20: with JDBC batching + synchronous_commit=off,
   *  DB throughput is higher so more concurrent writers can be kept busy. */
  @Bean(name = "dbWriterThreadPool", destroyMethod = "shutdown")
  public ExecutorService dbWriterThreadPool() {
    return new ThreadPoolExecutor(
        10, 20,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(200),
        namedFactory("db-writer"),
        new ThreadPoolExecutor.CallerRunsPolicy());
  }

  /** Scheduled pool for periodic stats aggregation and batch flush. */
  @Bean(name = "statsThreadPool", destroyMethod = "shutdown")
  public ScheduledExecutorService statsThreadPool() {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
        2, namedFactory("stats"));
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  private ThreadFactory namedFactory(String prefix) {
    AtomicInteger counter = new AtomicInteger();
    return r -> {
      Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
  }
}
