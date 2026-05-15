package com.kriyanshtech.bodycam.recording.transcript;

import java.nio.file.Path;
import java.util.UUID;

public interface RecordingTranscriptEngine {
    String key();

    default String label() {
        return key();
    }

    default String configuredEndpoint() {
        return null;
    }

    default boolean isReady() {
        String endpoint = configuredEndpoint();
        return endpoint != null && !endpoint.isBlank();
    }

    RecordingTranscriptGenerationResult generate(Path sourceVideoPath, UUID recordingId, UUID transcriptId) throws Exception;
}
