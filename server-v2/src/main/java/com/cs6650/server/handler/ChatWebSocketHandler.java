package com.cs6650.server.handler;

import com.cs6650.server.metrics.ServerMetrics;
import com.cs6650.server.model.ChatMessage;
import com.cs6650.server.queue.QueuePublisher;
import com.cs6650.server.service.RoomSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final QueuePublisher queuePublisher;
  private final RoomSessionRegistry roomSessionRegistry;
  private final ServerMetrics metrics;
  private final String serverId;
  private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();

  public ChatWebSocketHandler(
      QueuePublisher queuePublisher,
      RoomSessionRegistry roomSessionRegistry,
      ServerMetrics metrics,
      @Value("${server.instance-id}") String serverId) {
    this.queuePublisher = queuePublisher;
    this.roomSessionRegistry = roomSessionRegistry;
    this.metrics = metrics;
    this.serverId = serverId;
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
    ChatMessage message = objectMapper.readValue(textMessage.getPayload(), ChatMessage.class);
    message.ensureDefaults();

    if (!message.isValid()) {
      session.sendMessage(new TextMessage("{\"error\":\"Invalid message\"}"));
      return;
    }

    message.setServerId(serverId);
    if (session.getRemoteAddress() != null) {
      message.setClientIp(session.getRemoteAddress().toString());
    }

    String roomId = message.getRoomId();
    sessionToRoom.put(session.getId(), roomId);
    roomSessionRegistry.join(roomId, message.getUserId(), message.getUsername(), session);

    try {
      String payload = objectMapper.writeValueAsString(message);
      queuePublisher.publish(roomId, payload);
      metrics.incPublishedOk();
    } catch (Exception ex) {
      metrics.incPublishedFailed();
      session.sendMessage(new TextMessage("{\"error\":\"Publish failed\"}"));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomId = sessionToRoom.remove(session.getId());
    if (roomId != null) {
      roomSessionRegistry.leave(roomId, session);
    }
  }
}
