package com.kriyanshtech.bodycam.recording.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SessionTranscriptSegmentResponse(
        UUID id,
        UUID recordingId,
        Integer recordingSequence,
        Integer segmentIndex,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String text,
        BigDecimal confidence
) {
}
