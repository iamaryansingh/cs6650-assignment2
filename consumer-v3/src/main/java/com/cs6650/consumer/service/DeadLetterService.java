package com.cs6650.consumer.service;

import com.cs6650.consumer.entity.ChatMessageEntity;
import com.cs6650.consumer.entity.DeadLetterEntity;
import com.cs6650.consumer.repository.DeadLetterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assignment 3 Part 2: Dead letter storage for messages that fail to persist
 * after all retries. Messages are saved as JSON to the dead_letter_messages table
 * for manual inspection and recovery.
 */
@Service
public class DeadLetterService {

  private static final Logger log = LoggerFactory.getLogger(DeadLetterService.class);

  private final DeadLetterRepository repository;
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final AtomicLong deadLetterCount = new AtomicLong(0);

  // Self-injection so saveBatch() calls save() via the Spring proxy,
  // ensuring REQUIRES_NEW propagation is honoured (self-calls bypass the proxy).
  @Autowired
  @Lazy
  private DeadLetterService self;

  public DeadLetterService(DeadLetterRepository repository) {
    this.repository = repository;
  }

  /**
   * Saves one dead-letter entry in its own independent transaction.
   * REQUIRES_NEW ensures a prior failed transaction on the same connection
   * cannot bleed over and cause "25P02: current transaction is aborted".
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void save(ChatMessageEntity entity, String errorMessage) {
    DeadLetterEntity dead = new DeadLetterEntity();
    try {
      dead.setOriginalMessage(objectMapper.writeValueAsString(entity));
    } catch (Exception e) {
      dead.setOriginalMessage("{\"error\":\"serialization failed\"}");
    }
    dead.setErrorMessage(errorMessage);
    repository.save(dead);
    deadLetterCount.incrementAndGet();
    log.warn("Message {} sent to dead-letter: {}", entity.getMessageId(), errorMessage);
  }

  // NOT transactional — each save() gets its own REQUIRES_NEW transaction via proxy.
  public void saveBatch(List<ChatMessageEntity> batch, String errorMessage) {
    for (ChatMessageEntity entity : batch) {
      try {
        self.save(entity, errorMessage);  // goes through proxy → REQUIRES_NEW honoured
      } catch (Exception e) {
        // Last resort — if we can't even write dead-letter, just log it
        log.error("CRITICAL: Failed to save dead-letter for messageId={}: {}",
            entity.getMessageId(), e.getMessage());
      }
    }
    log.warn("Batch of {} messages sent to dead-letter: {}", batch.size(), errorMessage);
  }

  public long getDeadLetterCount() {
    return deadLetterCount.get();
  }
}
