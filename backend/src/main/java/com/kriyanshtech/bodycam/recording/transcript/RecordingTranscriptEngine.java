package com.kriyanshtech.bodycam.recording.transcript;

import java.nio.file.Path;
import java.util.UUID;

public interface RecordingTranscriptEngine {
    String key();

    RecordingTranscriptGenerationResult generate(Path sourceVideoPath, UUID recordingId, UUID transcriptId) throws Exception;
}
