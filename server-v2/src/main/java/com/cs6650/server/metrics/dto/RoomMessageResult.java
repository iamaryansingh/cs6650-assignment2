package com.cs6650.server.metrics.dto;

import com.cs6650.server.entity.ChatMessageEntity;
import java.util.List;

public record RoomMessageResult(
    String roomId,
    String startTime,
    String endTime,
    int count,
    List<ChatMessageEntity> messages,
    long executionTimeMs
) {}
