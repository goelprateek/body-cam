package com.company.bodycam.session.dto;

import com.company.bodycam.session.entity.SessionStatus;

import java.time.Instant;
import java.util.UUID;

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
