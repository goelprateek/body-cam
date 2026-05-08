package com.company.bodycam.recording.dto;

import java.time.Instant;
import java.util.UUID;

public record RecordingResponse(
        UUID id,
        UUID sessionId,
        String roomName,
        String objectKey,
        String playbackUrl,
        Integer durationSeconds,
        Instant createdAt
) {
}
