package com.kriyanshtech.bodycam.recording.transcript;

import java.util.List;

public record ProcessedTranscriptResult(
        String fullText,
        List<TranscriptSegmentPayload> segments
) {
}
