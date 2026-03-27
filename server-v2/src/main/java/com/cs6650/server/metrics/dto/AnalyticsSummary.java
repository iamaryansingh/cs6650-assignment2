package com.cs6650.server.metrics.dto;

import java.util.List;
import java.util.Map;

public record AnalyticsSummary(
    long totalMessages,
    double messagesPerSecond,
    double messagesPerMinute,
    List<Map<String, Object>> topUsers,
    List<Map<String, Object>> topRooms,
    long executionTimeMs
) {}
