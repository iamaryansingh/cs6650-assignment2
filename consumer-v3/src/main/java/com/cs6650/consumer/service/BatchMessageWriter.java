package com.cs6650.consumer.service;

import com.cs6650.consumer.entity.ChatMessageEntity;
import com.cs6650.consumer.metrics.ConsumerMetrics;
import com.cs6650.consumer.repository.MessageRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment 3 Part 2: Write-behind batch writer.
 *
 * Flow:
 *   ConsumerWorker -> addMessage() -> ConcurrentLinkedQueue buffer
 *   -> when batchSize reached OR flushInterval elapsed
 *   -> drain buffer -> submit to dbWriterThreadPool
 *   -> writeBatch() -> ON CONFLICT DO NOTHING upserts
 *
 * Batch size and flush interval are configurable via application.properties
 * for the batch optimization tests (100/500/1000/5000 x 100ms/500ms/1000ms).
 */
@Service
public class BatchMessageWriter {

  private static final Logger log = LoggerFactory.getLogger(BatchMessageWriter.class);

  private final MessageRepository repository;
  private final DeadLetterService deadLetterService;
  private final ExecutorService dbWriterPool;
  private final ScheduledExecutorService statsPool;
  private final ConsumerMetrics metrics;

  @Value("${consumer.batch.size:500}")
  private int batchSize;

  @Value("${consumer.flush.interval.ms:500}")
  private long flushIntervalMs;

  @Value("${consumer.write.max-retries:3}")
  private int maxRetries;

  // Write-behind buffer — lock-free, shared across all consumer threads
  private final ConcurrentLinkedQueue<ChatMessageEntity> buffer = new ConcurrentLinkedQueue<>();
  private final AtomicInteger bufferCount = new AtomicInteger(0);

  // Metrics
  private final AtomicLong totalWritten = new AtomicLong(0);
  private final AtomicLong totalDuplicatesSkipped = new AtomicLong(0);
  private final AtomicLong totalBatches = new AtomicLong(0);
  private final AtomicLong totalWriteErrors = new AtomicLong(0);

  public BatchMessageWriter(
      MessageRepository repository,
      DeadLetterService deadLetterService,
      @Qualifier("dbWriterThreadPool") ExecutorService dbWriterPool,
      @Qualifier("statsThreadPool") ScheduledExecutorService statsPool,
      ConsumerMetrics metrics) {
    this.repository = repository;
    this.deadLetterService = deadLetterService;
    this.dbWriterPool = dbWriterPool;
    this.statsPool = statsPool;
    this.metrics = metrics;
  }

  @PostConstruct
  public void start() {
    log.info("BatchMessageWriter starting: batchSize={} flushIntervalMs={}", batchSize, flushIntervalMs);
    // Periodic flush — ensures messages don't sit in buffer beyond flushIntervalMs
    statsPool.scheduleAtFixedRate(
        this::triggerFlush,
        flushIntervalMs,
        flushIntervalMs,
        TimeUnit.MILLISECONDS);
    // Periodic stats log every 10 seconds
    statsPool.scheduleAtFixedRate(
        this::logStats,
        10, 10, TimeUnit.SECONDS);
  }

  /**
   * Called by ConsumerWorker for every message received from RabbitMQ.
   * Non-blocking: adds to buffer, triggers flush if batch is full.
   */
  public void addMessage(ChatMessageEntity entity) {
    buffer.offer(entity);
    if (bufferCount.incrementAndGet() >= batchSize) {
      triggerFlush();
    }
  }

  /** Drains the buffer and submits a write task to the DB writer pool. */
  private void triggerFlush() {
    if (bufferCount.get() == 0) {
      return;
    }
    List<ChatMessageEntity> batch = drain();
    if (!batch.isEmpty()) {
      dbWriterPool.submit(() -> writeBatchWithRetry(batch));
    }
  }

  /** Drains up to batchSize items from the buffer atomically. */
  private List<ChatMessageEntity> drain() {
    List<ChatMessageEntity> batch = new ArrayList<>(batchSize);
    ChatMessageEntity entity;
    while (batch.size() < batchSize && (entity = buffer.poll()) != null) {
      batch.add(entity);
      bufferCount.decrementAndGet();
    }
    return batch;
  }

  /** Retry wrapper with exponential backoff. Falls back to dead-letter after maxRetries. */
  private void writeBatchWithRetry(List<ChatMessageEntity> batch) {
    long delayMs = 100;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        writeBatch(batch);
        return;
      } catch (Exception e) {
        log.warn("DB write attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
        if (attempt == maxRetries) {
          totalWriteErrors.addAndGet(batch.size());
          deadLetterService.saveBatch(batch, e.getMessage());
          return;
        }
        try {
          Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          return;
        }
        delayMs *= 2; // exponential backoff: 100 -> 200 -> 400 ms
      }
    }
  }

  /**
   * Actual database write — annotated with @CircuitBreaker (Resilience4j).
   * If DB is down, the circuit opens and messages go to dead-letter instead.
   */
  @CircuitBreaker(name = "databaseWriter", fallbackMethod = "writeBatchFallback")
  @Transactional
  public void writeBatch(List<ChatMessageEntity> batch) {
    int written = 0;
    for (ChatMessageEntity entity : batch) {
      int rows = repository.upsertIgnoreDuplicate(entity);
      if (rows > 0) {
        written++;
      } else {
        totalDuplicatesSkipped.incrementAndGet();
        metrics.incDuplicatesSkipped();
      }
    }
    totalWritten.addAndGet(written);
    totalBatches.incrementAndGet();
    log.debug("Batch written: {}/{} rows inserted, {} duplicates skipped",
        written, batch.size(), batch.size() - written);
  }

  /** Circuit breaker fallback — sends entire batch to dead-letter. */
  public void writeBatchFallback(List<ChatMessageEntity> batch, Exception e) {
    log.error("Circuit breaker OPEN — routing {} messages to dead-letter: {}", batch.size(), e.getMessage());
    totalWriteErrors.addAndGet(batch.size());
    deadLetterService.saveBatch(batch, "circuit-breaker-open: " + e.getMessage());
  }

  private void logStats() {
    log.info("BatchWriter stats: written={} batches={} duplicatesSkipped={} errors={} bufferSize={}",
        totalWritten.get(), totalBatches.get(), totalDuplicatesSkipped.get(),
        totalWriteErrors.get(), bufferCount.get());
  }

  @PreDestroy
  public void shutdown() {
    log.info("BatchMessageWriter shutting down — flushing remaining {} messages", bufferCount.get());
    // Flush remaining synchronously on shutdown
    List<ChatMessageEntity> remaining = drain();
    if (!remaining.isEmpty()) {
      writeBatchWithRetry(remaining);
    }
  }

  // Expose for health endpoint
  public long getTotalWritten() { return totalWritten.get(); }
  public long getTotalDuplicatesSkipped() { return totalDuplicatesSkipped.get(); }
  public long getTotalErrors() { return totalWriteErrors.get(); }
  public int getBufferSize() { return bufferCount.get(); }
}
