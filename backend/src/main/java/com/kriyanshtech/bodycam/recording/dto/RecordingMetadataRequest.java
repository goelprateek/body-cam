package com.kriyanshtech.bodycam.recording.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

public record RecordingMetadataRequest(
        Instant capturedAt,
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
