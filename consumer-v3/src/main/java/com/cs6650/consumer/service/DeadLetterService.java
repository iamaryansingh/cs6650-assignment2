package com.cs6650.consumer.service;

import com.cs6650.consumer.entity.ChatMessageEntity;
import com.cs6650.consumer.entity.DeadLetterEntity;
import com.cs6650.consumer.repository.DeadLetterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

  public DeadLetterService(DeadLetterRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void save(ChatMessageEntity entity, String errorMessage) {
    try {
      DeadLetterEntity dead = new DeadLetterEntity();
      dead.setOriginalMessage(objectMapper.writeValueAsString(entity));
      dead.setErrorMessage(errorMessage);
      repository.save(dead);
      deadLetterCount.incrementAndGet();
      log.warn("Message {} sent to dead-letter: {}", entity.getMessageId(), errorMessage);
    } catch (Exception e) {
      // Last resort — if we can't even write dead-letter, just log it
      log.error("CRITICAL: Failed to save dead-letter for messageId={}: {}",
          entity.getMessageId(), e.getMessage());
    }
  }

  @Transactional
  public void saveBatch(List<ChatMessageEntity> batch, String errorMessage) {
    for (ChatMessageEntity entity : batch) {
      save(entity, errorMessage);
    }
    log.warn("Batch of {} messages sent to dead-letter: {}", batch.size(), errorMessage);
  }

  public long getDeadLetterCount() {
    return deadLetterCount.get();
  }
}
