package com.cs6650.consumer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class ChatMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "message_id", nullable = false, unique = true, columnDefinition = "uuid")
  private UUID messageId;

  @Column(name = "room_id", nullable = false, length = 50)
  private String roomId;

  @Column(name = "user_id", nullable = false, length = 50)
  private String userId;

  @Column(name = "username", length = 100)
  private String username;

  @Column(name = "message", columnDefinition = "text")
  private String message;

  @Column(name = "timestamp", nullable = false)
  private Instant timestamp;

  @Column(name = "message_type", length = 10)
  private String messageType;

  @Column(name = "server_id", length = 100)
  private String serverId;

  @Column(name = "client_ip", length = 45)
  private String clientIp;

  @Column(name = "processed_at")
  private Instant processedAt;

  public Long getId() { return id; }
  public UUID getMessageId() { return messageId; }
  public void setMessageId(UUID messageId) { this.messageId = messageId; }
  public String getRoomId() { return roomId; }
  public void setRoomId(String roomId) { this.roomId = roomId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public Instant getTimestamp() { return timestamp; }
  public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
  public String getMessageType() { return messageType; }
  public void setMessageType(String messageType) { this.messageType = messageType; }
  public String getServerId() { return serverId; }
  public void setServerId(String serverId) { this.serverId = serverId; }
  public String getClientIp() { return clientIp; }
  public void setClientIp(String clientIp) { this.clientIp = clientIp; }
  public Instant getProcessedAt() { return processedAt; }
  public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
