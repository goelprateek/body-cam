package com.kriyanshtech.bodycam.recording.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kriyanshtech.bodycam.common.CreatedAtUuidCursor;
import com.kriyanshtech.bodycam.common.CreatedAtUuidCursorCodec;
import com.kriyanshtech.bodycam.common.CursorPaginationSupport;
import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.recording.dto.CreateRecordingRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingArchiveSessionResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingMetadataRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingMetadataResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingPlaybackResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionRecordingIntegrityStatus;
import com.kriyanshtech.bodycam.recording.dto.SessionRecordingTimelineGapResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionRecordingTimelineResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionRecordingTimelineSegmentResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingMetadata;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.RecordingMetadataRepository;
import com.kriyanshtech.bodycam.session.entity.LiveSession;
import com.kriyanshtech.bodycam.session.repository.LiveSessionRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;

@Service
public class RecordingService {
    private static final int PLAYBACK_URL_EXPIRY_SECONDS = 300;
    private static final long TIMELINE_GAP_THRESHOLD_MS = 1_500L;
    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);

    private final RecordingAssetRepository recordingAssetRepository;
    private final RecordingMetadataRepository recordingMetadataRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final ObjectStorageService objectStorageService;
    private final CreatedAtUuidCursorCodec cursorCodec;
    private final ObjectMapper objectMapper;

    public RecordingService(
            RecordingAssetRepository recordingAssetRepository,
            RecordingMetadataRepository recordingMetadataRepository,
            LiveSessionRepository liveSessionRepository,
            ObjectStorageService objectStorageService,
            CreatedAtUuidCursorCodec cursorCodec, ObjectMapper objectMapper) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingMetadataRepository = recordingMetadataRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.objectStorageService = objectStorageService;
        this.cursorCodec = cursorCodec;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public com.kriyanshtech.bodycam.common.CursorPageResponse<RecordingResponse> listRecordingsCursor(String cursor,
            int size) {
        List<RecordingAsset> assets;
        if (cursor == null || cursor.trim().isEmpty()) {
            assets = recordingAssetRepository.findFirstPage(PageRequest.of(0, size + 1));
        } else {
            CreatedAtUuidCursor decodedCursor = cursorCodec.decode(cursor);
            assets = recordingAssetRepository.findNextPage(
                    decodedCursor.createdAt(),
                    decodedCursor.id(),
                    PageRequest.of(0, size + 1));
        }

        return CursorPaginationSupport.buildPage(
                assets,
                size,
                this::map,
                asset -> cursorCodec.encode(new CreatedAtUuidCursor(asset.getCreatedAt(), asset.getId())));
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> listRecordings() {
        List<RecordingResponse> recordings = recordingAssetRepository.findAllActiveByOrderByCreatedAtDesc().stream()
                .map(this::map).toList();
        log.info("Listed {} recordings", recordings.size());
        return recordings;
    }

    @Transactional(readOnly = true)
    public List<RecordingArchiveSessionResponse> listArchiveSessions() {
        List<RecordingAsset> recordings = recordingAssetRepository.findAllActiveByOrderByCreatedAtDesc();
        Map<UUID, List<RecordingAsset>> recordingsBySessionId = new HashMap<>();
        for (RecordingAsset recording : recordings) {
            recordingsBySessionId.computeIfAbsent(recording.getSession().getId(), _unused -> new ArrayList<>()).add(recording);
        }

        List<RecordingArchiveSessionResponse> summaries = recordingsBySessionId.entrySet().stream()
                .map(entry -> mapArchiveSession(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> right.latestCreatedAt().compareTo(left.latestCreatedAt()))
                .toList();
        log.info("Listed {} recording archive session summaries from {} active recordings", summaries.size(), recordings.size());
        return summaries;
    }

    @Transactional(readOnly = true)
    public RecordingPlaybackResponse playbackUrl(UUID recordingId) {
        RecordingAsset asset = recordingAssetRepository.findActiveById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));
        log.info("Generating playback URL for recordingId={} objectKey={}", recordingId, asset.getObjectKey());

        return new RecordingPlaybackResponse(
                asset.getId(),
                objectStorageService.presignedPlaybackUrl(asset.getObjectKey(), PLAYBACK_URL_EXPIRY_SECONDS),
                PLAYBACK_URL_EXPIRY_SECONDS);
    }

    @Transactional(readOnly = true)
    public SessionRecordingTimelineResponse sessionTimeline(UUID sessionId) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        List<RecordingAsset> recordings = recordingAssetRepository.findActiveBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .sorted(RecordingTimelineSupport.segmentTimelineComparator())
                .toList();
        log.info("Building session recording timeline sessionId={} segmentCount={}", sessionId, recordings.size());

        List<SessionRecordingTimelineSegmentResponse> segments = recordings.stream()
                .map(this::mapTimelineSegment)
                .toList();

        Long totalDurationMs = segments.stream()
                .map(SessionRecordingTimelineSegmentResponse::sessionElapsedEndMs)
                .filter(value -> value != null && value >= 0)
                .max(Long::compareTo)
                .orElseGet(() -> estimateTotalDurationMs(segments));

        TimelineIntegrityEvaluation integrity = evaluateTimelineIntegrity(session, segments);
        log.info(
                "Built session recording timeline sessionId={} segmentCount={} totalDurationMs={} integrityStatus={} hasTimelineGaps={} duplicateSegmentCount={} missingSequenceCount={} segmentsMissingTimingCount={}",
                sessionId,
                segments.size(),
                totalDurationMs,
                integrity.integrityStatus(),
                integrity.hasTimelineGaps(),
                integrity.duplicateSegmentCount(),
                integrity.missingSequenceCount(),
                integrity.segmentsMissingTimingCount());

        return new SessionRecordingTimelineResponse(
                session.getId(),
                session.getWorkerId(),
                session.getWorkerName(),
                session.getReferenceNumber(),
                session.getRoomName(),
                session.getStartedAt(),
                session.getEndedAt(),
                totalDurationMs,
                integrity.integrityStatus(),
                integrity.hasTimelineGaps(),
                integrity.duplicateSegmentCount(),
                integrity.missingSequenceCount(),
                integrity.segmentsMissingTimingCount(),
                integrity.gaps(),
                segments);
    }

    @Transactional
    public RecordingResponse createRecording(CreateRecordingRequest request) {
        LiveSession session = liveSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new NotFoundException("Session not found: " + request.sessionId()));
        log.info(
                "Creating recording metadata row for sessionId={} objectKey={} durationSeconds={} metadataPresent={}",
                request.sessionId(),
                request.objectKey(),
                request.durationSeconds(),
                request.metadata() != null);

        return saveRecording(
                session,
                request.objectKey(),
                buildIdempotencyKey(request.sessionId(), request.metadata()),
                null,
                request.durationSeconds(),
                request.metadata());
    }

    @Transactional
    public RecordingResponse uploadRecording(
            UUID sessionId,
            Integer durationSeconds,
            RecordingMetadataRequest metadata,
            MultipartFile file) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Recording segment file is required");
        }
        validateUploadMetadata(metadata);

        String idempotencyKey = buildIdempotencyKey(sessionId, metadata);
        if (idempotencyKey != null) {
            RecordingAsset existingRecording = recordingAssetRepository.findActiveByIdempotencyKey(idempotencyKey)
                    .orElse(null);
            if (existingRecording != null) {
                log.info(
                        "Reusing existing recording for duplicate upload sessionId={} idempotencyKey={} recordingId={} objectKey={}",
                        sessionId,
                        idempotencyKey,
                        existingRecording.getId(),
                        existingRecording.getObjectKey());
                return map(existingRecording);
            }
        }

        String extension = extensionFor(file.getOriginalFilename(), file.getContentType());
        String objectKey = "sessions/%s/%s%s".formatted(sessionId, UUID.randomUUID(), extension);
        log.info(
                "Uploading recording for sessionId={} originalFilename={} sizeBytes={} contentType={} durationSeconds={} metadataPresent={} objectKey={} idempotencyKey={}",
                sessionId,
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType(),
                durationSeconds,
                metadata != null,
                objectKey,
                idempotencyKey);

        try (var inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        } catch (IOException exception) {
            log.error("Failed to read uploaded recording stream for sessionId={} objectKey={}", sessionId, objectKey,
                    exception);
            throw new IllegalStateException("Failed to read recording segment", exception);
        }

        log.info("Uploaded recording content for sessionId={} objectKey={}", sessionId, objectKey);

        return saveRecording(
                session,
                objectKey,
                idempotencyKey,
                null,
                durationSeconds,
                metadata);
    }

    private void validateUploadMetadata(RecordingMetadataRequest metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException(
                    "Recording upload metadata is required and must include segmentSequence, sessionElapsedStartMs, and sessionElapsedEndMs");
        }
        if (metadata.segmentSequence() == null) {
            throw new IllegalArgumentException("Recording upload metadata segmentSequence is required");
        }
        if (metadata.sessionElapsedStartMs() == null) {
            throw new IllegalArgumentException("Recording upload metadata sessionElapsedStartMs is required");
        }
        if (metadata.sessionElapsedEndMs() == null) {
            throw new IllegalArgumentException("Recording upload metadata sessionElapsedEndMs is required");
        }
    }

    private RecordingResponse saveRecording(
            LiveSession session,
            String objectKey,
            String idempotencyKey,
            String playbackUrl,
            Integer durationSeconds,
            RecordingMetadataRequest metadata) {
        RecordingAsset recordingAsset = new RecordingAsset();
        recordingAsset.setId(UUID.randomUUID());
        recordingAsset.setSession(session);
        recordingAsset.setObjectKey(objectKey);
        recordingAsset.setIdempotencyKey(idempotencyKey);
        recordingAsset.setPlaybackUrl(playbackUrl);
        recordingAsset.setDurationSeconds(durationSeconds);
        recordingAsset.setActive(true);
        recordingAsset.setDeactivatedAt(null);
        recordingAsset.setCreatedAt(Instant.now());
        RecordingAsset savedRecording = recordingAssetRepository.save(recordingAsset);
        log.info(
                "Saved recording asset id={} sessionId={} objectKey={} durationSeconds={} idempotencyKey={}",
                savedRecording.getId(),
                session.getId(),
                objectKey,
                durationSeconds,
                idempotencyKey);

        if (metadata != null) {
            RecordingMetadata savedMetadata = new RecordingMetadata();
            savedMetadata.setRecording(savedRecording);
            savedMetadata.setCapturedAt(metadata.capturedAt());
            savedMetadata.setSegmentSequence(metadata.segmentSequence());
            savedMetadata.setSegmentStartedAt(metadata.segmentStartedAt());
            savedMetadata.setSegmentEndedAt(metadata.segmentEndedAt());
            savedMetadata.setSessionElapsedStartMs(metadata.sessionElapsedStartMs());
            savedMetadata.setSessionElapsedEndMs(metadata.sessionElapsedEndMs());
            savedMetadata.setLatitude(metadata.latitude());
            savedMetadata.setLongitude(metadata.longitude());
            savedMetadata.setAltitudeMeters(metadata.altitudeMeters());
            savedMetadata.setLocationAccuracyMeters(metadata.locationAccuracyMeters());
            savedMetadata.setCameraFacing(metadata.cameraFacing());
            savedMetadata.setThermalEnabled(metadata.thermalEnabled());
            savedMetadata.setThermalMinC(metadata.thermalMinC());
            savedMetadata.setThermalMaxC(metadata.thermalMaxC());
            savedMetadata.setThermalAvgC(metadata.thermalAvgC());
            savedMetadata.setSensorPayload(
                    metadata.sensorPayload() != null
                            ? objectMapper.valueToTree(metadata.sensorPayload())
                            : null);
            savedMetadata.setCreatedAt(Instant.now());
            savedRecording.setMetadata(recordingMetadataRepository.save(savedMetadata));
            log.info(
                    "Saved recording metadata for recordingId={} segmentSequence={} sessionElapsedStartMs={} sessionElapsedEndMs={} capturedAt={} cameraFacing={} hasLocation={} hasSensorPayload={}",
                    savedRecording.getId(),
                    metadata.segmentSequence(),
                    metadata.sessionElapsedStartMs(),
                    metadata.sessionElapsedEndMs(),
                    metadata.capturedAt(),
                    metadata.cameraFacing(),
                    metadata.latitude() != null && metadata.longitude() != null,
                    metadata.sensorPayload() != null);
        } else {
            log.warn("Recording saved without metadata for recordingId={} sessionId={}", savedRecording.getId(),
                    session.getId());
        }

        return map(savedRecording);
    }

    @Transactional
    public void deactivateSessionRecordings(UUID sessionId) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        List<RecordingAsset> recordings = recordingAssetRepository.findActiveBySessionIdOrderByCreatedAtAsc(sessionId);
        if (recordings.isEmpty()) {
            log.info("Session recordings already inactive sessionId={}", sessionId);
            return;
        }

        Instant deactivatedAt = Instant.now();
        for (RecordingAsset recording : recordings) {
            recording.setActive(false);
            recording.setDeactivatedAt(deactivatedAt);
        }
        recordingAssetRepository.saveAll(recordings);
        log.info("Soft deleted {} recording segment(s) for sessionId={} roomName={}",
                recordings.size(),
                session.getId(),
                session.getRoomName());
    }

    private String extensionFor(String filename, String contentType) {
        if (filename != null) {
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex >= 0) {
                return filename.substring(dotIndex);
            }
        }
        if (contentType != null && contentType.equalsIgnoreCase("video/mp4")) {
            return ".mp4";
        }
        return ".bin";
    }

    private RecordingResponse map(RecordingAsset asset) {
        return new RecordingResponse(
                asset.getId(),
                asset.getSession().getId(),
                asset.getSession().getWorkerId(),
                asset.getSession().getWorkerName(),
                asset.getSession().getReferenceNumber(),
                asset.getSession().getRoomName(),
                asset.getObjectKey(),
                presignedPlaybackUrl(asset),
                asset.getDurationSeconds(),
                asset.getCreatedAt(),
                mapMetadata(asset.getMetadata()),
                asset.getTranscript() == null ? null : asset.getTranscript().getStatus());
    }

    private RecordingArchiveSessionResponse mapArchiveSession(UUID sessionId, List<RecordingAsset> sessionRecordings) {
        List<RecordingAsset> latestFirstRecordings = sessionRecordings.stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
        List<RecordingAsset> timelineOrderedRecordings = sessionRecordings.stream()
                .sorted(RecordingTimelineSupport.segmentTimelineComparator())
                .toList();

        RecordingAsset latestRecording = latestFirstRecordings.getFirst();
        RecordingMetadata latestMetadata = latestRecording.getMetadata();
        Integer approxDurationSeconds = resolveApproxDurationSeconds(latestFirstRecordings);

        return new RecordingArchiveSessionResponse(
                sessionId,
                latestRecording.getSession().getWorkerName(),
                latestRecording.getSession().getRoomName(),
                latestRecording.getSession().getReferenceNumber(),
                latestRecording.getCreatedAt(),
                latestFirstRecordings.size(),
                approxDurationSeconds,
                latestMetadata == null || latestMetadata.getLatitude() == null ? null : latestMetadata.getLatitude().toPlainString(),
                latestMetadata == null || latestMetadata.getLongitude() == null ? null : latestMetadata.getLongitude().toPlainString(),
                resolvePreviewPlaybackUrl(timelineOrderedRecordings),
                resolveSessionTranscriptStatus(latestFirstRecordings));
    }

    private SessionRecordingTimelineSegmentResponse mapTimelineSegment(RecordingAsset asset) {
        RecordingMetadata metadata = asset.getMetadata();
        return new SessionRecordingTimelineSegmentResponse(
                asset.getId(),
                metadata == null ? null : metadata.getSegmentSequence(),
                asset.getObjectKey(),
                objectStorageService.presignedPlaybackUrl(asset.getObjectKey(), PLAYBACK_URL_EXPIRY_SECONDS),
                asset.getDurationSeconds(),
                asset.getCreatedAt(),
                metadata == null ? null : metadata.getCapturedAt(),
                metadata == null ? null : metadata.getSegmentStartedAt(),
                metadata == null ? null : metadata.getSegmentEndedAt(),
                metadata == null ? null : metadata.getSessionElapsedStartMs(),
                metadata == null ? null : metadata.getSessionElapsedEndMs(),
                asset.getTranscript() == null ? null : asset.getTranscript().getStatus());
    }

    private RecordingMetadataResponse mapMetadata(RecordingMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return new RecordingMetadataResponse(
                metadata.getCapturedAt(),
                metadata.getSegmentSequence(),
                metadata.getSegmentStartedAt(),
                metadata.getSegmentEndedAt(),
                metadata.getSessionElapsedStartMs(),
                metadata.getSessionElapsedEndMs(),
                metadata.getLatitude(),
                metadata.getLongitude(),
                metadata.getAltitudeMeters(),
                metadata.getLocationAccuracyMeters(),
                metadata.getCameraFacing(),
                metadata.getThermalEnabled(),
                metadata.getThermalMinC(),
                metadata.getThermalMaxC(),
                metadata.getThermalAvgC(),
                metadata.getSensorPayload());
    }

    private Long estimateTotalDurationMs(List<SessionRecordingTimelineSegmentResponse> segments) {
        long total = 0L;
        for (SessionRecordingTimelineSegmentResponse segment : segments) {
            if (segment.durationSeconds() != null && segment.durationSeconds() > 0) {
                total += segment.durationSeconds() * 1000L;
            }
        }
        return total;
    }

    private int resolveApproxDurationSeconds(List<RecordingAsset> recordings) {
        long maxEndMs = 0L;
        int totalDurationSeconds = 0;
        boolean hasEndMs = false;

        for (RecordingAsset recording : recordings) {
            RecordingMetadata metadata = recording.getMetadata();
            if (metadata != null && metadata.getSessionElapsedEndMs() != null) {
                hasEndMs = true;
                maxEndMs = Math.max(maxEndMs, metadata.getSessionElapsedEndMs());
            }
            if (recording.getDurationSeconds() != null) {
                totalDurationSeconds += recording.getDurationSeconds();
            }
        }

        if (hasEndMs && maxEndMs > 0) {
            return Math.toIntExact(Math.round(maxEndMs / 1000.0d));
        }
        return totalDurationSeconds;
    }

    private String resolvePreviewPlaybackUrl(List<RecordingAsset> timelineOrderedRecordings) {
        for (RecordingAsset recording : timelineOrderedRecordings) {
            String playbackUrl = presignedPlaybackUrl(recording);
            if (playbackUrl != null && !playbackUrl.isBlank()) {
                return playbackUrl;
            }
        }
        return null;
    }

    private RecordingTranscriptStatus resolveSessionTranscriptStatus(List<RecordingAsset> recordings) {
        List<RecordingTranscriptStatus> statuses = recordings.stream()
                .map(RecordingAsset::getTranscript)
                .filter(transcript -> transcript != null)
                .map(transcript -> transcript.getStatus())
                .toList();

        if (statuses.isEmpty()) {
            return null;
        }
        if (statuses.contains(RecordingTranscriptStatus.PROCESSING)) {
            return RecordingTranscriptStatus.PROCESSING;
        }
        if (statuses.contains(RecordingTranscriptStatus.PENDING)) {
            return RecordingTranscriptStatus.PENDING;
        }
        if (statuses.contains(RecordingTranscriptStatus.FAILED)) {
            return RecordingTranscriptStatus.FAILED;
        }
        if (statuses.contains(RecordingTranscriptStatus.READY)) {
            return RecordingTranscriptStatus.READY;
        }
        return RecordingTranscriptStatus.NOT_REQUESTED;
    }

    private TimelineIntegrityEvaluation evaluateTimelineIntegrity(
            LiveSession session,
            List<SessionRecordingTimelineSegmentResponse> segments) {
        Integer expectedNextSequence = null;
        Long previousEndMs = null;
        int duplicateSegmentCount = 0;
        int missingSequenceCount = 0;
        int segmentsMissingTimingCount = 0;
        boolean hasTimelineGaps = false;
        List<SessionRecordingTimelineGapResponse> gaps = new ArrayList<>();

        for (SessionRecordingTimelineSegmentResponse segment : segments) {
            boolean missingTimingForSegment = false;
            Integer sequence = segment.segmentSequence();
            if (sequence != null) {
                if (expectedNextSequence != null) {
                    if (sequence.equals(expectedNextSequence)) {
                        expectedNextSequence = sequence + 1;
                    } else if (sequence > expectedNextSequence) {
                        int missingCount = sequence - expectedNextSequence;
                        missingSequenceCount += missingCount;
                        expectedNextSequence = sequence + 1;
                        hasTimelineGaps = true;
                        gaps.add(new SessionRecordingTimelineGapResponse(
                                "MISSING_SEQUENCE",
                                "Missing " + missingCount + " expected upload segment(s) before segment "
                                        + (sequence + 1),
                                previousEndMs,
                                segment.sessionElapsedStartMs(),
                                missingCount));
                    } else {
                        duplicateSegmentCount++;
                        hasTimelineGaps = true;
                        gaps.add(new SessionRecordingTimelineGapResponse(
                                "DUPLICATE_SEQUENCE",
                                "Duplicate or out-of-order segment sequence detected at segment " + (sequence + 1),
                                segment.sessionElapsedStartMs(),
                                segment.sessionElapsedEndMs(),
                                1));
                    }
                } else {
                    expectedNextSequence = sequence + 1;
                }
            }

            if (previousEndMs != null
                    && segment.sessionElapsedStartMs() != null
                    && segment.sessionElapsedStartMs() > previousEndMs + TIMELINE_GAP_THRESHOLD_MS) {
                hasTimelineGaps = true;
                gaps.add(new SessionRecordingTimelineGapResponse(
                        "TIMING_GAP",
                        "Playback gap detected between uploaded segments.",
                        previousEndMs,
                        segment.sessionElapsedStartMs(),
                        null));
            }

            if (previousEndMs != null
                    && segment.sessionElapsedStartMs() != null
                    && segment.sessionElapsedStartMs() < previousEndMs - TIMELINE_GAP_THRESHOLD_MS) {
                hasTimelineGaps = true;
                gaps.add(new SessionRecordingTimelineGapResponse(
                        "TIMING_OVERLAP",
                        "Segment timing overlaps with a previous clip and should be reviewed.",
                        segment.sessionElapsedStartMs(),
                        previousEndMs,
                        null));
            }

            if (segment.sessionElapsedEndMs() != null) {
                previousEndMs = segment.sessionElapsedEndMs();
            } else if (segment.sessionElapsedStartMs() != null && segment.durationSeconds() != null) {
                previousEndMs = segment.sessionElapsedStartMs() + (segment.durationSeconds() * 1000L);
            } else {
                missingTimingForSegment = true;
            }

            if (segment.sessionElapsedStartMs() == null || segment.sessionElapsedEndMs() == null) {
                missingTimingForSegment = true;
            }

            if (missingTimingForSegment) {
                segmentsMissingTimingCount++;
                gaps.add(new SessionRecordingTimelineGapResponse(
                        "MISSING_TIMING",
                        "Segment timing metadata is missing for one or more clips.",
                        segment.sessionElapsedStartMs(),
                        segment.sessionElapsedEndMs(),
                        1));
            }
        }

        SessionRecordingIntegrityStatus integrityStatus;
        if (hasTimelineGaps || duplicateSegmentCount > 0 || missingSequenceCount > 0) {
            integrityStatus = SessionRecordingIntegrityStatus.HAS_GAPS;
        } else if (session.getEndedAt() == null) {
            integrityStatus = SessionRecordingIntegrityStatus.PROCESSING_UPLOADS;
        } else if (segments.isEmpty() || segmentsMissingTimingCount > 0) {
            integrityStatus = SessionRecordingIntegrityStatus.PARTIAL;
        } else {
            integrityStatus = SessionRecordingIntegrityStatus.COMPLETE;
        }

        return new TimelineIntegrityEvaluation(
                integrityStatus,
                hasTimelineGaps,
                duplicateSegmentCount,
                missingSequenceCount,
                segmentsMissingTimingCount,
                gaps);
    }

    private String presignedPlaybackUrl(RecordingAsset asset) {
        if (asset.getObjectKey() == null || asset.getObjectKey().isBlank()) {
            return null;
        }
        return objectStorageService.presignedPlaybackUrl(asset.getObjectKey(), PLAYBACK_URL_EXPIRY_SECONDS);
    }

    private String buildIdempotencyKey(UUID sessionId, RecordingMetadataRequest metadata) {
        if (sessionId == null || metadata == null || metadata.segmentSequence() == null) {
            return null;
        }
        return sessionId + ":" + metadata.segmentSequence();
    }

    private record TimelineIntegrityEvaluation(
            SessionRecordingIntegrityStatus integrityStatus,
            boolean hasTimelineGaps,
            int duplicateSegmentCount,
            int missingSequenceCount,
            int segmentsMissingTimingCount,
            List<SessionRecordingTimelineGapResponse> gaps) {
    }
}
