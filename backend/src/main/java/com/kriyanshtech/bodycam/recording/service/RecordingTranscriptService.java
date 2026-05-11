package com.kriyanshtech.bodycam.recording.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptSegmentResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingMetadata;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptSegment;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.RecordingTranscriptRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RecordingTranscriptService {
    private static final Logger log = LoggerFactory.getLogger(RecordingTranscriptService.class);
    private static final String STUB_ENGINE = "stub";
    private static final String STUB_MODEL = "phase-1";
    private static final DateTimeFormatter TRANSCRIPT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final RecordingAssetRepository recordingAssetRepository;
    private final RecordingTranscriptRepository recordingTranscriptRepository;

    public RecordingTranscriptService(
            RecordingAssetRepository recordingAssetRepository,
            RecordingTranscriptRepository recordingTranscriptRepository
    ) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingTranscriptRepository = recordingTranscriptRepository;
    }

    @Transactional(readOnly = true)
    public RecordingTranscriptResponse getTranscript(UUID recordingId) {
        RecordingAsset recording = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));

        return recordingTranscriptRepository.findByRecording_Id(recordingId)
                .map(this::map)
                .orElseGet(() -> {
                    log.info("Transcript not yet requested for recordingId={}", recordingId);
                    return new RecordingTranscriptResponse(
                            null,
                            recording.getId(),
                            RecordingTranscriptStatus.NOT_REQUESTED,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            List.of()
                    );
                });
    }

    @Transactional
    public RecordingTranscriptResponse generateTranscript(UUID recordingId) {
        RecordingAsset recording = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));

        RecordingTranscript transcript = recordingTranscriptRepository.findByRecording_Id(recordingId)
                .orElseGet(() -> createTranscript(recording));

        Instant now = Instant.now();
        transcript.setStatus(RecordingTranscriptStatus.PROCESSING);
        transcript.setEngine(STUB_ENGINE);
        transcript.setModel(STUB_MODEL);
        transcript.setLanguageCode("en");
        transcript.setErrorMessage(null);
        transcript.setStartedAt(now);
        transcript.setCompletedAt(null);
        transcript.setUpdatedAt(now);
        recordingTranscriptRepository.save(transcript);
        log.info("Transcript generation requested recordingId={} transcriptId={} status={}",
                recordingId, transcript.getId(), transcript.getStatus());

        try {
            List<RecordingTranscriptSegment> segments = buildStubSegments(recording, transcript, now);
            transcript.replaceSegments(segments);
            transcript.setFullText(joinSegments(segments));
            transcript.setStatus(RecordingTranscriptStatus.READY);
            transcript.setCompletedAt(Instant.now());
            transcript.setUpdatedAt(Instant.now());
            RecordingTranscript savedTranscript = recordingTranscriptRepository.save(transcript);
            log.info("Transcript stub generated recordingId={} transcriptId={} segmentCount={}",
                    recordingId, savedTranscript.getId(), savedTranscript.getSegments().size());
            return map(savedTranscript);
        } catch (Exception exception) {
            transcript.replaceSegments(List.of());
            transcript.setStatus(RecordingTranscriptStatus.FAILED);
            transcript.setErrorMessage(exception.getMessage());
            transcript.setCompletedAt(Instant.now());
            transcript.setUpdatedAt(Instant.now());
            recordingTranscriptRepository.save(transcript);
            log.error("Transcript generation failed recordingId={} transcriptId={}",
                    recordingId, transcript.getId(), exception);
            return map(transcript);
        }
    }

    private RecordingTranscript createTranscript(RecordingAsset recording) {
        RecordingTranscript transcript = new RecordingTranscript();
        Instant now = Instant.now();
        transcript.setId(UUID.randomUUID());
        transcript.setRecording(recording);
        transcript.setStatus(RecordingTranscriptStatus.PENDING);
        transcript.setCreatedAt(now);
        transcript.setUpdatedAt(now);
        return transcript;
    }

    private List<RecordingTranscriptSegment> buildStubSegments(
            RecordingAsset recording,
            RecordingTranscript transcript,
            Instant now
    ) {
        List<RecordingTranscriptSegment> segments = new ArrayList<>();
        segments.add(segment(
                transcript,
                0,
                decimal(0),
                decimal(8),
                "Stub transcript for worker " + recording.getSession().getWorkerName()
                        + " in room " + recording.getSession().getRoomName() + "."
                        + " This placeholder is here so the operator flow can be validated before"
                        + " a real speech engine is wired in.",
                now
        ));
        segments.add(segment(
                transcript,
                1,
                decimal(8),
                decimal(16),
                "Recording captured at " + TRANSCRIPT_TIME_FORMATTER.format(recording.getCreatedAt())
                        + " against reference number " + recording.getSession().getReferenceNumber() + ".",
                now
        ));

        RecordingMetadata metadata = recording.getMetadata();
        if (metadata != null && metadata.getLatitude() != null && metadata.getLongitude() != null) {
            segments.add(segment(
                    transcript,
                    2,
                    decimal(16),
                    decimal(24),
                    "Location metadata was captured near latitude " + metadata.getLatitude()
                            + " and longitude " + metadata.getLongitude() + ".",
                    now
            ));
        } else {
            segments.add(segment(
                    transcript,
                    2,
                    decimal(16),
                    decimal(24),
                    "No location metadata was available for this recording segment.",
                    now
            ));
        }

        return segments;
    }

    private RecordingTranscriptSegment segment(
            RecordingTranscript transcript,
            int segmentIndex,
            BigDecimal startSeconds,
            BigDecimal endSeconds,
            String text,
            Instant now
    ) {
        RecordingTranscriptSegment segment = new RecordingTranscriptSegment();
        segment.setId(UUID.randomUUID());
        segment.setTranscript(transcript);
        segment.setSegmentIndex(segmentIndex);
        segment.setStartSeconds(startSeconds);
        segment.setEndSeconds(endSeconds);
        segment.setText(text);
        segment.setConfidence(decimal(1));
        segment.setCreatedAt(now);
        return segment;
    }

    private BigDecimal decimal(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String joinSegments(List<RecordingTranscriptSegment> segments) {
        return segments.stream()
                .map(RecordingTranscriptSegment::getText)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private RecordingTranscriptResponse map(RecordingTranscript transcript) {
        return new RecordingTranscriptResponse(
                transcript.getId(),
                transcript.getRecording().getId(),
                transcript.getStatus(),
                transcript.getEngine(),
                transcript.getModel(),
                transcript.getLanguageCode(),
                transcript.getFullText(),
                transcript.getErrorMessage(),
                transcript.getStartedAt(),
                transcript.getCompletedAt(),
                transcript.getCreatedAt(),
                transcript.getUpdatedAt(),
                transcript.getSegments().stream().map(this::mapSegment).toList()
        );
    }

    private RecordingTranscriptSegmentResponse mapSegment(RecordingTranscriptSegment segment) {
        return new RecordingTranscriptSegmentResponse(
                segment.getId(),
                segment.getSegmentIndex(),
                segment.getStartSeconds(),
                segment.getEndSeconds(),
                segment.getText(),
                segment.getConfidence()
        );
    }
}
