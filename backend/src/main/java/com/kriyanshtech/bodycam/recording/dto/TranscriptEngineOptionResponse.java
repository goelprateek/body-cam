package com.kriyanshtech.bodycam.recording.dto;

public record TranscriptEngineOptionResponse(
        String key,
        String label,
        boolean configuredDefault,
        boolean ready,
        String endpoint
) {
}
