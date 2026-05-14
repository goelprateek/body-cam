package com.kriyanshtech.bodycam.recording.dto;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptProcessingStage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionTranscriptResponse(
        UUID sessionId,
        RecordingTranscriptStatus status,
        String engine,
        String model,
        String languageCode,
        String fullText,
        String shortSummary,
        String incidentSummary,
        List<String> keywords,
        String errorMessage,
        RecordingTranscriptProcessingStage processingStage,
        Instant lastStageAt,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt,
        Integer totalRecordings,
        Integer readyRecordings,
        Integer failedRecordings,
        Integer processingRecordings,
        Integer pendingRecordings,
        Integer notRequestedRecordings,
        List<SessionTranscriptSegmentResponse> segments,
        List<SessionTranscriptRecordingResponse> recordings
) {
}
