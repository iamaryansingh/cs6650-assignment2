package com.cs6650.server.metrics.dto;

public record RoomActivity(String roomId, String lastActivity, long messageCount) {}
