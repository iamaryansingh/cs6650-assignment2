package com.cs6650.server.metrics.dto;

public record ActiveUsersResult(
    long activeUserCount,
    String startTime,
    String endTime,
    long executionTimeMs
) {}
