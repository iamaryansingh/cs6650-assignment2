package com.cs6650.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Read-only JPA entity for the Metrics API — mirrors the messages table written by consumer-v3. */
@Entity
@Table(name = "messages")
public class ChatMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "message_id", columnDefinition = "uuid")
  private UUID messageId;

  @Column(name = "room_id")
  private String roomId;

  @Column(name = "user_id")
  private String userId;

  @Column(name = "username")
  private String username;

  @Column(name = "message")
  private String message;

  @Column(name = "timestamp")
  private Instant timestamp;

  @Column(name = "message_type")
  private String messageType;

  @Column(name = "server_id")
  private String serverId;

  @Column(name = "client_ip")
  private String clientIp;

  @Column(name = "processed_at")
  private Instant processedAt;

  public Long getId() { return id; }
  public UUID getMessageId() { return messageId; }
  public String getRoomId() { return roomId; }
  public String getUserId() { return userId; }
  public String getUsername() { return username; }
  public String getMessage() { return message; }
  public Instant getTimestamp() { return timestamp; }
  public String getMessageType() { return messageType; }
  public String getServerId() { return serverId; }
  public String getClientIp() { return clientIp; }
  public Instant getProcessedAt() { return processedAt; }
}
