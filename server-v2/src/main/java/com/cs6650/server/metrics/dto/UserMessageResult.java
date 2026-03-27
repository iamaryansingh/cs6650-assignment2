package com.cs6650.server.metrics.dto;

import com.cs6650.server.entity.ChatMessageEntity;
import java.util.List;

public record UserMessageResult(
    String userId,
    int count,
    List<ChatMessageEntity> messages,
    long executionTimeMs
) {}
