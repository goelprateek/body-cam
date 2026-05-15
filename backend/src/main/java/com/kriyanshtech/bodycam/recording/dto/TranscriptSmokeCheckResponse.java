package com.kriyanshtech.bodycam.recording.dto;

import java.util.List;

public record TranscriptSmokeCheckResponse(
        boolean ready,
        boolean enabled,
        String engine,
        String endpoint,
        long pollDelayMs,
        int maxRetryCount,
        java.util.List<TranscriptEngineOptionResponse> availableEngines,
        List<String> checks,
        List<String> warnings
) {
}
