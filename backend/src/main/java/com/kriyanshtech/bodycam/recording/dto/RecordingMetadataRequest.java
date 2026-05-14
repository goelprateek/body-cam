package com.kriyanshtech.bodycam.recording.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

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
                Map<String, Object> sensorPayload) {
}
