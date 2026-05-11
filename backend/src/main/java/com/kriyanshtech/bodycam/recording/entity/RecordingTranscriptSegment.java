package com.kriyanshtech.bodycam.recording.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording_transcript_segment")
public class RecordingTranscriptSegment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transcript_id", nullable = false)
    private RecordingTranscript transcript;

    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex;

    @Column(name = "start_seconds", nullable = false, precision = 8, scale = 2)
    private BigDecimal startSeconds;

    @Column(name = "end_seconds", nullable = false, precision = 8, scale = 2)
    private BigDecimal endSeconds;

    @Column(name = "text", nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public RecordingTranscript getTranscript() {
        return transcript;
    }

    public void setTranscript(RecordingTranscript transcript) {
        this.transcript = transcript;
    }

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(Integer segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public BigDecimal getStartSeconds() {
        return startSeconds;
    }

    public void setStartSeconds(BigDecimal startSeconds) {
        this.startSeconds = startSeconds;
    }

    public BigDecimal getEndSeconds() {
        return endSeconds;
    }

    public void setEndSeconds(BigDecimal endSeconds) {
        this.endSeconds = endSeconds;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
