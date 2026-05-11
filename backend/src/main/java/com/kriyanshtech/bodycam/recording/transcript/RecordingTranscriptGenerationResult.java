package com.kriyanshtech.bodycam.recording.transcript;

import java.util.List;

public record RecordingTranscriptGenerationResult(
        String engine,
        String model,
        String languageCode,
        String fullText,
        List<TranscriptSegmentPayload> segments
) {
}
