package com.kriyanshtech.bodycam.recording.transcript;

import java.math.BigDecimal;

public record TranscriptSegmentPayload(
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String text,
        BigDecimal confidence
) {
}
