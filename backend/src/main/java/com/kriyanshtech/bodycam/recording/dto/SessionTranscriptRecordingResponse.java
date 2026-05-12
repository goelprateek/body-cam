package com.kriyanshtech.bodycam.recording.dto;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionTranscriptRecordingResponse(
        UUID recordingId,
        Integer recordingSequence,
        RecordingTranscriptStatus status,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        Long sessionElapsedStartMs,
        Long sessionElapsedEndMs,
        Integer durationSeconds,
        Integer transcriptSegmentCount
) {
}
