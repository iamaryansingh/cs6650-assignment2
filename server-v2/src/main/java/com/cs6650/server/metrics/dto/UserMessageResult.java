package com.cs6650.server.metrics.dto;

import java.util.List;
import java.util.Map;

public record UserMessageResult(
    String userId,
    int count,
    List<Map<String, Object>> messages,
    long executionTimeMs
) {}
