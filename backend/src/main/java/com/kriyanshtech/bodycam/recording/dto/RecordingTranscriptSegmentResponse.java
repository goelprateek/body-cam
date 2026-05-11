package com.kriyanshtech.bodycam.recording.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordingTranscriptSegmentResponse(
        UUID id,
        Integer segmentIndex,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String text,
        BigDecimal confidence
) {
}
