package com.kriyanshtech.bodycam.session.dto;

import com.kriyanshtech.bodycam.session.entity.SessionStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionInviteResponse(
        UUID id,
        UUID sessionId,
        String workerName,
        String referenceNumber,
        String roomName,
        SessionStatus sessionStatus,
        String participantRole,
        String inviteToken,
        String joinPath,
        Instant expiresAt,
        Instant createdAt
) {
}
