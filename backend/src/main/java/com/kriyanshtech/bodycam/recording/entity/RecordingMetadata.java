package com.kriyanshtech.bodycam.recording.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording_metadata")
public class RecordingMetadata {

    @Id
    @Column(name = "recording_id")
    private UUID recordingId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recording_id", nullable = false)
    private RecordingAsset recording;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "segment_sequence")
    private Integer segmentSequence;

    @Column(name = "segment_started_at")
    private Instant segmentStartedAt;

    @Column(name = "segment_ended_at")
    private Instant segmentEndedAt;

    @Column(name = "session_elapsed_start_ms")
    private Long sessionElapsedStartMs;

    @Column(name = "session_elapsed_end_ms")
    private Long sessionElapsedEndMs;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "altitude_meters", precision = 8, scale = 2)
    private BigDecimal altitudeMeters;

    @Column(name = "location_accuracy_meters", precision = 8, scale = 2)
    private BigDecimal locationAccuracyMeters;

    @Column(name = "camera_facing", length = 16)
    private String cameraFacing;

    @Column(name = "thermal_enabled")
    private Boolean thermalEnabled;

    @Column(name = "thermal_min_c", precision = 6, scale = 2)
    private BigDecimal thermalMinC;

    @Column(name = "thermal_max_c", precision = 6, scale = 2)
    private BigDecimal thermalMaxC;

    @Column(name = "thermal_avg_c", precision = 6, scale = 2)
    private BigDecimal thermalAvgC;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sensor_payload")
    private JsonNode sensorPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(UUID recordingId) {
        this.recordingId = recordingId;
    }

    public RecordingAsset getRecording() {
        return recording;
    }

    public void setRecording(RecordingAsset recording) {
        this.recording = recording;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public Integer getSegmentSequence() {
        return segmentSequence;
    }

    public void setSegmentSequence(Integer segmentSequence) {
        this.segmentSequence = segmentSequence;
    }

    public Instant getSegmentStartedAt() {
        return segmentStartedAt;
    }

    public void setSegmentStartedAt(Instant segmentStartedAt) {
        this.segmentStartedAt = segmentStartedAt;
    }

    public Instant getSegmentEndedAt() {
        return segmentEndedAt;
    }

    public void setSegmentEndedAt(Instant segmentEndedAt) {
        this.segmentEndedAt = segmentEndedAt;
    }

    public Long getSessionElapsedStartMs() {
        return sessionElapsedStartMs;
    }

    public void setSessionElapsedStartMs(Long sessionElapsedStartMs) {
        this.sessionElapsedStartMs = sessionElapsedStartMs;
    }

    public Long getSessionElapsedEndMs() {
        return sessionElapsedEndMs;
    }

    public void setSessionElapsedEndMs(Long sessionElapsedEndMs) {
        this.sessionElapsedEndMs = sessionElapsedEndMs;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getAltitudeMeters() {
        return altitudeMeters;
    }

    public void setAltitudeMeters(BigDecimal altitudeMeters) {
        this.altitudeMeters = altitudeMeters;
    }

    public BigDecimal getLocationAccuracyMeters() {
        return locationAccuracyMeters;
    }

    public void setLocationAccuracyMeters(BigDecimal locationAccuracyMeters) {
        this.locationAccuracyMeters = locationAccuracyMeters;
    }

    public String getCameraFacing() {
        return cameraFacing;
    }

    public void setCameraFacing(String cameraFacing) {
        this.cameraFacing = cameraFacing;
    }

    public Boolean getThermalEnabled() {
        return thermalEnabled;
    }

    public void setThermalEnabled(Boolean thermalEnabled) {
        this.thermalEnabled = thermalEnabled;
    }

    public BigDecimal getThermalMinC() {
        return thermalMinC;
    }

    public void setThermalMinC(BigDecimal thermalMinC) {
        this.thermalMinC = thermalMinC;
    }

    public BigDecimal getThermalMaxC() {
        return thermalMaxC;
    }

    public void setThermalMaxC(BigDecimal thermalMaxC) {
        this.thermalMaxC = thermalMaxC;
    }

    public BigDecimal getThermalAvgC() {
        return thermalAvgC;
    }

    public void setThermalAvgC(BigDecimal thermalAvgC) {
        this.thermalAvgC = thermalAvgC;
    }

    public JsonNode getSensorPayload() {
        return sensorPayload;
    }

    public void setSensorPayload(JsonNode sensorPayload) {
        this.sensorPayload = sensorPayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
