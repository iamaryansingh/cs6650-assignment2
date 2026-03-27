package com.cs6650.server.metrics.dto;

import java.util.List;
import java.util.Map;

public record RoomMessageResult(
    String roomId,
    String startTime,
    String endTime,
    int count,
    List<Map<String, Object>> messages,
    long executionTimeMs
) {}
