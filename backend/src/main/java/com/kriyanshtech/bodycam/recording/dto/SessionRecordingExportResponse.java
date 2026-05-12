package com.kriyanshtech.bodycam.recording.dto;

import com.kriyanshtech.bodycam.recording.entity.SessionRecordingExportStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionRecordingExportResponse(
        UUID id,
        UUID sessionId,
        SessionRecordingExportStatus status,
        String objectKey,
        String downloadUrl,
        Integer expiresInSeconds,
        Long packageSizeBytes,
        Integer artifactCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
