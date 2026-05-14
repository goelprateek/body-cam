package com.kriyanshtech.bodycam.recording.dto;

import java.util.List;

public record TranscriptSmokeCheckResponse(
        boolean ready,
        boolean enabled,
        String configuredEngine,
        String configuredEndpoint,
        long pollDelayMs,
        List<String> checks,
        List<String> warnings
) {
}
