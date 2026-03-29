package com.cs6650.server.service;

import com.cs6650.server.model.UserInfo;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RoomSessionRegistry {
  private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, UserInfo> activeUsers = new ConcurrentHashMap<>();
  private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
  private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();
  private final Map<String, String> sessionToUsername = new ConcurrentHashMap<>();
  private final Map<String, ReentrantLock> sessionSendLocks = new ConcurrentHashMap<>();

  public void join(String roomId, String userId, String username, WebSocketSession session) {
    roomSessions.computeIfAbsent(roomId, id -> ConcurrentHashMap.newKeySet()).add(session);
    sessionSendLocks.computeIfAbsent(session.getId(), id -> new ReentrantLock());
    if (userId != null && !userId.isBlank()) {
      sessionToUser.put(session.getId(), userId);
      if (username != null && !username.isBlank()) {
        sessionToUsername.put(session.getId(), username);
      }
      userSessions.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(session);
      UserInfo userInfo = activeUsers.computeIfAbsent(userId, UserInfo::new);
      userInfo.onSessionJoin(session.getId(), username, roomId);
    }
  }

  public void leave(String roomId, WebSocketSession session) {
    Set<WebSocketSession> sessions = roomSessions.get(roomId);
    if (sessions == null) {
      return;
    }
    sessions.remove(session);
    if (sessions.isEmpty()) {
      roomSessions.remove(roomId);
    }
    sessionSendLocks.remove(session.getId());

    String userId = sessionToUser.remove(session.getId());
    sessionToUsername.remove(session.getId());
    if (userId != null) {
      Set<WebSocketSession> userSet = userSessions.get(userId);
      if (userSet != null) {
        userSet.remove(session);
        if (userSet.isEmpty()) {
          userSessions.remove(userId);
        }
      }
      UserInfo userInfo = activeUsers.get(userId);
      if (userInfo != null) {
        userInfo.onSessionLeave(session.getId());
        if (userInfo.hasNoSessions()) {
          activeUsers.remove(userId);
        }
      }
    }
  }

  public int broadcast(String roomId, String payload) {
    Set<WebSocketSession> sessions = roomSessions.get(roomId);
    if (sessions == null || sessions.isEmpty()) {
      return 0;
    }
    int sent = 0;
    TextMessage textMessage = new TextMessage(payload);
    for (WebSocketSession session : sessions) {
      if (!session.isOpen()) {
        continue;
      }
      try {
        ReentrantLock lock = sessionSendLocks.computeIfAbsent(session.getId(), id -> new ReentrantLock());
        lock.lock();
        try {
          session.sendMessage(textMessage);
        } finally {
          lock.unlock();
        }
        sent++;
      } catch (Exception ignored) {
        // session closed mid-send — skip it
      }
    }
    return sent;
  }

  // don't try to send if the client already disconnected — just skip it
  public void sendDirect(WebSocketSession session, String payload) throws IOException {
    if (!session.isOpen()) return;
    ReentrantLock lock = sessionSendLocks.computeIfAbsent(session.getId(), id -> new ReentrantLock());
    lock.lock();
    try {
      if (session.isOpen()) {
        session.sendMessage(new TextMessage(payload));
      }
    } finally {
      lock.unlock();
    }
  }

  public int roomCount() {
    return roomSessions.size();
  }

  public int sessionCount() {
    int total = 0;
    for (Set<WebSocketSession> sessions : roomSessions.values()) {
      total += sessions.size();
    }
    return total;
  }

  public int activeUserCount() {
    return activeUsers.size();
  }

  public int sessionsForUser(String userId) {
    Set<WebSocketSession> sessions = userSessions.get(userId);
    return sessions == null ? 0 : sessions.size();
  }
}
