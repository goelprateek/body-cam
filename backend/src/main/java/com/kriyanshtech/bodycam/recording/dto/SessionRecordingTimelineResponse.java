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
        SessionRecordingIntegrityStatus integrityStatus,
        boolean hasTimelineGaps,
        int duplicateSegmentCount,
        int missingSequenceCount,
        int segmentsMissingTimingCount,
        List<SessionRecordingTimelineGapResponse> gaps,
        List<SessionRecordingTimelineSegmentResponse> segments
) {
}
