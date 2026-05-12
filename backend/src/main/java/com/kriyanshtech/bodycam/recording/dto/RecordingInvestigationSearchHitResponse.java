package com.kriyanshtech.bodycam.recording.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RecordingInvestigationSearchHitResponse(
        UUID sessionId,
        UUID recordingId,
        Integer recordingSequence,
        UUID workerId,
        String workerName,
        String roomName,
        String referenceNumber,
        String matchedField,
        String snippet,
        BigDecimal transcriptStartSeconds,
        Instant createdAt
) {
}
