package com.cs6650.consumer.model;

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
