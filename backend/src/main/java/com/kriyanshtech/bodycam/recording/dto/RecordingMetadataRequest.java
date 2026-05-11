package com.kriyanshtech.bodycam.recording.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record RecordingMetadataRequest(
        Instant capturedAt,
        Integer segmentSequence,
        Instant segmentStartedAt,
        Instant segmentEndedAt,
        Long sessionElapsedStartMs,
        Long sessionElapsedEndMs,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal altitudeMeters,
        BigDecimal locationAccuracyMeters,
        String cameraFacing,
        Boolean thermalEnabled,
        BigDecimal thermalMinC,
        BigDecimal thermalMaxC,
        BigDecimal thermalAvgC,
        JsonNode sensorPayload
) {
}
