package com.cs6650.consumer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "dead_letter_messages")
public class DeadLetterEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "original_message", nullable = false, columnDefinition = "jsonb")
  private String originalMessage;

  @Column(name = "error_message", columnDefinition = "text")
  private String errorMessage;

  @Column(name = "retry_count")
  private int retryCount = 0;

  @Column(name = "created_at")
  private Instant createdAt = Instant.now();

  @Column(name = "last_retry_at")
  private Instant lastRetryAt;

  public Long getId() { return id; }
  public String getOriginalMessage() { return originalMessage; }
  public void setOriginalMessage(String originalMessage) { this.originalMessage = originalMessage; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
  public Instant getLastRetryAt() { return lastRetryAt; }
  public void setLastRetryAt(Instant lastRetryAt) { this.lastRetryAt = lastRetryAt; }
}
