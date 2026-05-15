package com.kriyanshtech.bodycam.recording.dto;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionRecordingTimelineSegmentResponse(
        UUID recordingId,
        Integer segmentSequence,
        String objectKey,
        String playbackUrl,
        Integer durationSeconds,
        Instant createdAt,
        Instant capturedAt,
        Instant segmentStartedAt,
        Instant segmentEndedAt,
        Long sessionElapsedStartMs,
        Long sessionElapsedEndMs,
        RecordingTranscriptStatus transcriptStatus
) {
}
