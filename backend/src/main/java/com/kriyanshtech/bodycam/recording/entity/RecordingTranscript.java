package com.kriyanshtech.bodycam.recording.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recording_transcript")
public class RecordingTranscript {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recording_id", nullable = false, unique = true)
    private RecordingAsset recording;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RecordingTranscriptStatus status;

    @Column(name = "engine", length = 64)
    private String engine;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "language_code", length = 16)
    private String languageCode;

    @Column(name = "full_text", columnDefinition = "text")
    private String fullText;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_stage", length = 32)
    private RecordingTranscriptProcessingStage processingStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_error_stage", length = 32)
    private RecordingTranscriptProcessingStage lastErrorStage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "last_stage_at")
    private Instant lastStageAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "transcript", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("segmentIndex ASC")
    private List<RecordingTranscriptSegment> segments = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RecordingAsset getRecording() {
        return recording;
    }

    public void setRecording(RecordingAsset recording) {
        this.recording = recording;
    }

    public RecordingTranscriptStatus getStatus() {
        return status;
    }

    public void setStatus(RecordingTranscriptStatus status) {
        this.status = status;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public RecordingTranscriptProcessingStage getProcessingStage() {
        return processingStage;
    }

    public void setProcessingStage(RecordingTranscriptProcessingStage processingStage) {
        this.processingStage = processingStage;
    }

    public RecordingTranscriptProcessingStage getLastErrorStage() {
        return lastErrorStage;
    }

    public void setLastErrorStage(RecordingTranscriptProcessingStage lastErrorStage) {
        this.lastErrorStage = lastErrorStage;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getLastStageAt() {
        return lastStageAt;
    }

    public void setLastStageAt(Instant lastStageAt) {
        this.lastStageAt = lastStageAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<RecordingTranscriptSegment> getSegments() {
        return segments;
    }

    public void replaceSegments(List<RecordingTranscriptSegment> segments) {
        this.segments.clear();
        for (RecordingTranscriptSegment segment : segments) {
            segment.setTranscript(this);
            this.segments.add(segment);
        }
    }
}
