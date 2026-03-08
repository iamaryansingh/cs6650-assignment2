package com.cs6650.server.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UserInfo {
  private final String userId;
  private volatile String username;
  private volatile String currentRoomId;
  private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();
  private volatile long lastSeenEpochMs;

  public UserInfo(String userId) {
    this.userId = userId;
    this.lastSeenEpochMs = System.currentTimeMillis();
  }

  public void onSessionJoin(String sessionId, String username, String roomId) {
    this.username = username;
    this.currentRoomId = roomId;
    this.lastSeenEpochMs = System.currentTimeMillis();
    this.sessionIds.add(sessionId);
  }

  public void onSessionLeave(String sessionId) {
    this.sessionIds.remove(sessionId);
    this.lastSeenEpochMs = System.currentTimeMillis();
  }

  public boolean hasNoSessions() {
    return sessionIds.isEmpty();
  }

  public String getUserId() { return userId; }
  public String getUsername() { return username; }
  public String getCurrentRoomId() { return currentRoomId; }
  public Set<String> getSessionIds() { return sessionIds; }
  public long getLastSeenEpochMs() { return lastSeenEpochMs; }
}
