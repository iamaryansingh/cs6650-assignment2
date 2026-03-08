package com.cs6650.client.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

public class ChatMessage {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public String messageId;
  public String roomId;
  public String userId;
  public String username;
  public String message;
  public String timestamp;
  public String messageType;

  public ChatMessage() {
    this.messageId = UUID.randomUUID().toString();
    this.timestamp = Instant.now().toString();
    this.messageType = "TEXT";
  }

  public String toJson() throws JsonProcessingException {
    return OBJECT_MAPPER.writeValueAsString(this);
  }
}
