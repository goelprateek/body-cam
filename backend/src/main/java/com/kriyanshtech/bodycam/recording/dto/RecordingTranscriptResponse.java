package com.kriyanshtech.bodycam.recording.dto;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptProcessingStage;

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
        RecordingTranscriptProcessingStage processingStage,
        Instant lastStageAt,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        List<RecordingTranscriptSegmentResponse> segments
) {
}
