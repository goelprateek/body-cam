package com.kriyanshtech.bodycam.recording.dto;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RecordingTranscriptResponse(
        UUID id,
        UUID recordingId,
        RecordingTranscriptStatus status,
        String engine,
        String model,
        String languageCode,
        String fullText,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        List<RecordingTranscriptSegmentResponse> segments
) {
}
