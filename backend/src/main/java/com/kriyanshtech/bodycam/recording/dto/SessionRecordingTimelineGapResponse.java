package com.kriyanshtech.bodycam.recording.dto;

public record SessionRecordingTimelineGapResponse(
        String type,
        String label,
        Long startMs,
        Long endMs,
        Integer missingCount
) {
}
