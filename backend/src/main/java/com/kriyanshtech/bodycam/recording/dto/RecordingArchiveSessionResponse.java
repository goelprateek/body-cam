package com.kriyanshtech.bodycam.recording.dto;

import java.time.Instant;
import java.util.UUID;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;

public record RecordingArchiveSessionResponse(
        UUID sessionId,
        String workerName,
        String roomName,
        String referenceNumber,
        Instant latestCreatedAt,
        int recordingCount,
        int approxDurationSeconds,
        String latitude,
        String longitude,
        String previewPlaybackUrl,
        RecordingTranscriptStatus transcriptStatus
) {
}
