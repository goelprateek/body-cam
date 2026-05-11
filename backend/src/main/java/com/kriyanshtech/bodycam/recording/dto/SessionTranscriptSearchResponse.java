package com.kriyanshtech.bodycam.recording.dto;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;

import java.util.List;
import java.util.UUID;

public record SessionTranscriptSearchResponse(
        UUID sessionId,
        String query,
        RecordingTranscriptStatus status,
        int totalMatches,
        List<SessionTranscriptSegmentResponse> matches
) {
}
