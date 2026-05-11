package com.kriyanshtech.bodycam.recording.dto;

import java.time.Instant;
import java.util.UUID;

public record RecordingResponse(
        UUID id,
        UUID sessionId,
        UUID workerId,
        String workerName,
        String roomName,
        String objectKey,
        String playbackUrl,
        Integer durationSeconds,
        Instant createdAt,
        RecordingMetadataResponse metadata
) {
}
