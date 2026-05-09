package com.kriyanshtech.bodycam.session.dto;

import java.time.Instant;
import java.util.UUID;

import com.kriyanshtech.bodycam.session.entity.SessionStatus;

public record SessionResponse(
        UUID id,
        UUID workerId,
        String workerName,
        String roomName,
        SessionStatus status,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt
) {
}
