package com.cs6650.server.metrics.dto;

import java.util.List;

public record UserRoomsResult(
    String userId,
    List<RoomActivity> rooms,
    long executionTimeMs
) {}
