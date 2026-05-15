package com.kriyanshtech.bodycam.session.dto;

import com.kriyanshtech.bodycam.session.entity.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record PublicSessionInviteResponse(
        UUID sessionId,
        String workerName,
        String referenceNumber,
        String roomName,
        SessionStatus sessionStatus,
        String participantRole,
        Instant expiresAt
) {
}
