package com.kriyanshtech.bodycam.recording.dto;

import java.time.Instant;
import java.util.UUID;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;

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
        RecordingMetadataResponse metadata,
        RecordingTranscriptStatus transcriptStatus
) {
}
