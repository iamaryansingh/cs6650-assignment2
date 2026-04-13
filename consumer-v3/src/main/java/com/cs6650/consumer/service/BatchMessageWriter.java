package com.cs6650.consumer.service;

import com.cs6650.consumer.entity.ChatMessageEntity;
import com.cs6650.consumer.metrics.ConsumerMetrics;
import com.cs6650.consumer.repository.MessageRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment 3 Part 2: Write-behind batch writer.
 *
 * Flow:
 *   ConsumerWorker -> addMessage() -> ConcurrentLinkedQueue buffer
 *   -> when batchSize reached OR flushInterval elapsed
 *   -> drain buffer -> submit to dbWriterThreadPool
 *   -> writeBatch() -> single JDBC batchUpdate (one round trip to PostgreSQL)
 *
 * Why JdbcTemplate.batchUpdate() instead of per-row upsertIgnoreDuplicate():
 *   The previous code called upsertIgnoreDuplicate() inside a for-loop — one SQL
 *   statement per message, meaning 1000 messages = 1000 separate network round trips
 *   to PostgreSQL even inside one transaction. JdbcTemplate.batchUpdate() sends all
 *   rows in a single PreparedStatement.executeBatch() call — one round trip total.
 *   Combined with synchronous_commit=off (set via connection-init-sql), this removes
 *   the fsync wait on each transaction, giving 3-8x write throughput improvement.
 */
@Service
public class BatchMessageWriter {

  private static final Logger log = LoggerFactory.getLogger(BatchMessageWriter.class);

  private static final String UPSERT_SQL =
      "INSERT INTO messages " +
      "(message_id, room_id, user_id, username, message, timestamp, " +
      " message_type, server_id, client_ip, processed_at) " +
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
      "ON CONFLICT (message_id) DO NOTHING";

  private final JdbcTemplate jdbcTemplate;
  private final MessageRepository repository;
  private final DeadLetterService deadLetterService;
  private final ExecutorService dbWriterPool;
  private final ScheduledExecutorService statsPool;
  private final ConsumerMetrics metrics;

  @Value("${consumer.batch.size:2000}")
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

  // Snapshot for per-interval DB write rate
  private final AtomicLong lastWrittenSnapshot = new AtomicLong(0);
  private volatile long lastStatsMs = System.currentTimeMillis();

  public BatchMessageWriter(
      JdbcTemplate jdbcTemplate,
      MessageRepository repository,
      DeadLetterService deadLetterService,
      @Qualifier("dbWriterThreadPool") ExecutorService dbWriterPool,
      @Qualifier("statsThreadPool") ScheduledExecutorService statsPool,
      ConsumerMetrics metrics) {
    this.jdbcTemplate = jdbcTemplate;
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

  /** Drains up to batchSize items from the buffer. */
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
        delayMs *= 2;
      }
    }
  }

  /**
   * Actual database write using JDBC batch — ONE round trip for the whole batch.
   *
   * Returns int[] where each element is the update count for that row:
   *   1 = inserted, 0 = skipped (ON CONFLICT DO NOTHING = duplicate).
   *
   * synchronous_commit=off is set per-connection via HikariCP connection-init-sql,
   * so the transaction commits without waiting for WAL fsync. ~3x throughput gain.
   */
  @CircuitBreaker(name = "databaseWriter", fallbackMethod = "writeBatchFallback")
  @Transactional
  public void writeBatch(List<ChatMessageEntity> batch) {
    int[] results = jdbcTemplate.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        ChatMessageEntity e = batch.get(i);
        ps.setObject(1, e.getMessageId());                            // UUID
        ps.setString(2, e.getRoomId());
        ps.setString(3, e.getUserId());
        ps.setString(4, e.getUsername());
        ps.setString(5, e.getMessage());
        ps.setTimestamp(6, e.getTimestamp() != null
            ? Timestamp.from(e.getTimestamp()) : new Timestamp(System.currentTimeMillis()));
        ps.setString(7, e.getMessageType());
        ps.setString(8, e.getServerId());
        ps.setString(9, e.getClientIp());
      }

      @Override
      public int getBatchSize() {
        return batch.size();
      }
    });

    int written = 0;
    int dupes = 0;
    for (int r : results) {
      if (r > 0) {
        written++;
      } else {
        dupes++;
      }
    }
    totalWritten.addAndGet(written);
    totalDuplicatesSkipped.addAndGet(dupes);
    totalBatches.incrementAndGet();
    if (dupes > 0) {
      metrics.incDuplicatesSkipped(dupes);
    }
    log.debug("Batch written: {}/{} inserted, {} duplicates skipped", written, batch.size(), dupes);
  }

  /** Circuit breaker fallback — sends entire batch to dead-letter. */
  public void writeBatchFallback(List<ChatMessageEntity> batch, Exception e) {
    log.error("Circuit breaker OPEN — routing {} messages to dead-letter: {}", batch.size(), e.getMessage());
    totalWriteErrors.addAndGet(batch.size());
    deadLetterService.saveBatch(batch, "circuit-breaker-open: " + e.getMessage());
  }

  private void logStats() {
    long nowMs          = System.currentTimeMillis();
    long currentWritten = totalWritten.get();
    long prevWritten    = lastWrittenSnapshot.getAndSet(currentWritten);
    double elapsedSec  = Math.max(1, nowMs - lastStatsMs) / 1000.0;
    lastStatsMs         = nowMs;
    double writeRate    = (currentWritten - prevWritten) / elapsedSec;
    double consumeRate  = metrics.getAndResetConsumeRate();

    log.info("[CONSUMER] window={} msg/s consumed | [DB-WRITER] window={} msg/s written"
             + " | total written={} | batches={} | errors={} | buffer={}",
        String.format("%,.0f", consumeRate),
        String.format("%,.0f", writeRate),
        currentWritten, totalBatches.get(), totalWriteErrors.get(), bufferCount.get());
  }

  @PreDestroy
  public void shutdown() {
    log.info("BatchMessageWriter shutting down — flushing remaining {} messages", bufferCount.get());
    List<ChatMessageEntity> remaining = drain();
    if (!remaining.isEmpty()) {
      writeBatchWithRetry(remaining);
    }
  }

  // Expose for health endpoint
  public long getTotalWritten()           { return totalWritten.get(); }
  public long getTotalDuplicatesSkipped() { return totalDuplicatesSkipped.get(); }
  public long getTotalErrors()            { return totalWriteErrors.get(); }
  public int  getBufferSize()             { return bufferCount.get(); }
}
