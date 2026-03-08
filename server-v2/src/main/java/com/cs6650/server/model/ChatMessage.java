package com.cs6650.server.model;

import java.time.Instant;
import java.util.UUID;

public class ChatMessage {
  private String messageId;
  private String roomId;
  private String userId;
  private String username;
  private String message;
  private String timestamp;
  private String messageType;
  private String serverId;
  private String clientIp;

  public void ensureDefaults() {
    if (messageId == null || messageId.isBlank()) {
      messageId = UUID.randomUUID().toString();
    }
    if (timestamp == null || timestamp.isBlank()) {
      timestamp = Instant.now().toString();
    }
    if (messageType == null || messageType.isBlank()) {
      messageType = "TEXT";
    }
  }

  public boolean isValid() {
    return roomId != null && !roomId.isBlank() && userId != null && !userId.isBlank();
  }

  public String getMessageId() { return messageId; }
  public void setMessageId(String messageId) { this.messageId = messageId; }
  public String getRoomId() { return roomId; }
  public void setRoomId(String roomId) { this.roomId = roomId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
  public String getTimestamp() { return timestamp; }
  public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
  public String getMessageType() { return messageType; }
  public void setMessageType(String messageType) { this.messageType = messageType; }
  public String getServerId() { return serverId; }
  public void setServerId(String serverId) { this.serverId = serverId; }
  public String getClientIp() { return clientIp; }
  public void setClientIp(String clientIp) { this.clientIp = clientIp; }
}
