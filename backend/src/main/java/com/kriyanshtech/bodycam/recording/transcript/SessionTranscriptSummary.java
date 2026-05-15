package com.kriyanshtech.bodycam.recording.transcript;

import java.util.List;

public record SessionTranscriptSummary(
        String shortSummary,
        String incidentSummary,
        List<String> keywords
) {
}
