package com.kriyanshtech.bodycam.recording.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionRecordingTimelineResponse(
        UUID sessionId,
        UUID workerId,
        String workerName,
        String referenceNumber,
        String roomName,
        Instant sessionStartedAt,
        Instant sessionEndedAt,
        Long totalDurationMs,
        boolean hasTimelineGaps,
        List<SessionRecordingTimelineSegmentResponse> segments
) {
}
