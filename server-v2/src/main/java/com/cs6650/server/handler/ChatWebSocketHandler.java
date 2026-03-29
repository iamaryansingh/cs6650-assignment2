package com.cs6650.server.handler;

import com.cs6650.server.metrics.ServerMetrics;
import com.cs6650.server.model.ChatMessage;
import com.cs6650.server.queue.QueuePublisher;
import com.cs6650.server.service.RoomSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
    if (message.getRoomId() == null || message.getRoomId().isBlank()) {
      message.setRoomId(extractRoomIdFromUri(session));
    }

    if (!message.isValid()) {
      sendErrorResponse(session, List.of("Invalid message"));
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
      sendErrorResponse(session, List.of("Publish failed"));
      return;
    }
    sendSuccessResponse(session, message);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String roomId = sessionToRoom.remove(session.getId());
    if (roomId != null) {
      roomSessionRegistry.leave(roomId, session);
    }
  }

  // if the session closed before we could reply, just move on — no need to crash the handler
  private void sendSuccessResponse(WebSocketSession session, ChatMessage message) {
    try {
      Map<String, Object> response = Map.of(
          "status", "success",
          "messageId", message.getMessageId(),
          "roomId", message.getRoomId(),
          "serverTimestamp", Instant.now().toString(),
          "messageType", message.getMessageType());
      roomSessionRegistry.sendDirect(session, objectMapper.writeValueAsString(response));
    } catch (Exception ignored) {
      // Session closed between message receipt and response — not an error
    }
  }

  private void sendErrorResponse(WebSocketSession session, List<String> errors) {
    try {
      Map<String, Object> response = Map.of(
          "status", "error",
          "errors", errors,
          "serverTimestamp", Instant.now().toString());
      roomSessionRegistry.sendDirect(session, objectMapper.writeValueAsString(response));
    } catch (Exception ignored) {
      // Session closed — nothing to do
    }
  }

  private String extractRoomIdFromUri(WebSocketSession session) {
    if (session.getUri() == null || session.getUri().getPath() == null) {
      return null;
    }
    String path = session.getUri().getPath();
    int idx = path.lastIndexOf('/');
    if (idx >= 0 && idx < path.length() - 1) {
      String candidate = path.substring(idx + 1);
      if (!candidate.isBlank() && !"chat".equals(candidate)) {
        return candidate;
      }
    }
    return null;
  }
}
