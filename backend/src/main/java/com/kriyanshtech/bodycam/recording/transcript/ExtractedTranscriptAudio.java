package com.kriyanshtech.bodycam.recording.transcript;

import java.nio.file.Path;

public record ExtractedTranscriptAudio(
        Path wavPath,
        float sampleRate
) {
}
