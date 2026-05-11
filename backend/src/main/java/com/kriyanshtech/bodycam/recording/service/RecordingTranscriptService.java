package com.kriyanshtech.bodycam.recording.service;

import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.config.AppProperties;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptSegmentResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptSegmentResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptSegment;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.RecordingTranscriptRepository;
import com.kriyanshtech.bodycam.recording.transcript.RecordingTranscriptEngine;
import com.kriyanshtech.bodycam.recording.transcript.RecordingTranscriptGenerationResult;
import com.kriyanshtech.bodycam.recording.transcript.TranscriptSegmentPayload;
import com.kriyanshtech.bodycam.session.repository.LiveSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RecordingTranscriptService {
    private static final Logger log = LoggerFactory.getLogger(RecordingTranscriptService.class);

    private final RecordingAssetRepository recordingAssetRepository;
    private final RecordingTranscriptRepository recordingTranscriptRepository;
    private final ObjectStorageService objectStorageService;
    private final AppProperties appProperties;
    private final Map<String, RecordingTranscriptEngine> transcriptEngines;
    private final LiveSessionRepository liveSessionRepository;

    public RecordingTranscriptService(
            RecordingAssetRepository recordingAssetRepository,
            RecordingTranscriptRepository recordingTranscriptRepository,
            ObjectStorageService objectStorageService,
            AppProperties appProperties,
            LiveSessionRepository liveSessionRepository,
            List<RecordingTranscriptEngine> transcriptEngines) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingTranscriptRepository = recordingTranscriptRepository;
        this.objectStorageService = objectStorageService;
        this.appProperties = appProperties;
        this.liveSessionRepository = liveSessionRepository;
        this.transcriptEngines = indexEngines(transcriptEngines);
    }

    @Transactional(readOnly = true)
    public RecordingTranscriptResponse getTranscript(UUID recordingId) {
        RecordingAsset recording = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));

        return recordingTranscriptRepository.findByRecording_Id(recordingId)
                .map(this::map)
                .orElseGet(() -> notRequested(recording));
    }

    @Transactional
    public RecordingTranscriptResponse generateTranscript(UUID recordingId) {
        if (!appProperties.transcript().enabled()) {
            throw new IllegalStateException("Transcript generation is disabled in backend configuration");
        }

        RecordingAsset recording = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));
        return generateTranscript(recording);
    }

    @Transactional(readOnly = true)
    public SessionTranscriptResponse getSessionTranscript(UUID sessionId) {
        List<RecordingAsset> recordings = orderedSessionRecordings(sessionId);
        return aggregateSessionTranscript(sessionId, recordings);
    }

    public SessionTranscriptResponse generateSessionTranscript(UUID sessionId) {
        if (!appProperties.transcript().enabled()) {
            throw new IllegalStateException("Transcript generation is disabled in backend configuration");
        }

        List<RecordingAsset> recordings = orderedSessionRecordings(sessionId);
        int generatedCount = 0;
        log.info("Session transcript generation started sessionId={} recordingCount={}", sessionId, recordings.size());

        for (RecordingAsset recording : recordings) {
            RecordingTranscript transcript = recording.getTranscript();
            if (transcript != null && transcript.getStatus() == RecordingTranscriptStatus.READY) {
                continue;
            }
            generateTranscript(recording);
            generatedCount++;
        }

        log.info("Session transcript generation completed sessionId={} generatedRecordings={}", sessionId, generatedCount);
        return aggregateSessionTranscript(sessionId, orderedSessionRecordings(sessionId));
    }

    @Transactional(readOnly = true)
    public String buildSessionSubtitleVtt(UUID sessionId) {
        SessionTranscriptResponse transcript = getSessionTranscript(sessionId);
        if (transcript.segments().isEmpty()) {
            throw new IllegalStateException("Session transcript subtitles are not available yet");
        }

        StringBuilder builder = new StringBuilder("WEBVTT\n\n");
        for (int index = 0; index < transcript.segments().size(); index++) {
            SessionTranscriptSegmentResponse segment = transcript.segments().get(index);
            builder.append(index + 1).append('\n')
                    .append(formatVttTimestamp(segment.startSeconds()))
                    .append(" --> ")
                    .append(formatVttTimestamp(segment.endSeconds()))
                    .append('\n')
                    .append(HtmlUtils.htmlEscape(segment.text()))
                    .append("\n\n");
        }
        return builder.toString();
    }

    private RecordingTranscriptResponse generateTranscript(RecordingAsset recording) {
        UUID recordingId = recording.getId();

        RecordingTranscript transcript = recordingTranscriptRepository.findByRecording_Id(recordingId)
                .orElseGet(() -> createTranscript(recording));

        RecordingTranscriptEngine engine = requiredEngine();
        Instant startedAt = Instant.now();
        transcript.setStatus(RecordingTranscriptStatus.PROCESSING);
        transcript.setEngine(engine.key());
        transcript.setModel(null);
        transcript.setLanguageCode(appProperties.transcript().languageCode());
        transcript.setErrorMessage(null);
        transcript.setStartedAt(startedAt);
        transcript.setCompletedAt(null);
        transcript.setUpdatedAt(startedAt);
        transcript.replaceSegments(List.of());
        recordingTranscriptRepository.save(transcript);
        log.info("Transcript generation started recordingId={} transcriptId={} objectKey={} engine={}",
                recordingId, transcript.getId(), recording.getObjectKey(), engine.key());

        Path sourceVideoPath = null;
        try {
            sourceVideoPath = downloadRecordingToTempFile(recording);
            RecordingTranscriptGenerationResult result = engine.generate(sourceVideoPath, recordingId,
                    transcript.getId());

            transcript.setEngine(result.engine());
            transcript.setModel(result.model());
            transcript.setLanguageCode(result.languageCode());
            transcript.setFullText(result.fullText());
            transcript.replaceSegments(toSegments(transcript, result.segments(), Instant.now()));
            transcript.setStatus(RecordingTranscriptStatus.READY);
            transcript.setCompletedAt(Instant.now());
            transcript.setUpdatedAt(Instant.now());
            RecordingTranscript savedTranscript = recordingTranscriptRepository.save(transcript);
            log.info("Transcript generation completed recordingId={} transcriptId={} engine={} segmentCount={}",
                    recordingId, savedTranscript.getId(), result.engine(), savedTranscript.getSegments().size());
            return map(savedTranscript);
        } catch (Exception exception) {
            transcript.replaceSegments(List.of());
            transcript.setStatus(RecordingTranscriptStatus.FAILED);
            transcript.setErrorMessage(exception.getMessage());
            transcript.setCompletedAt(Instant.now());
            transcript.setUpdatedAt(Instant.now());
            recordingTranscriptRepository.save(transcript);
            log.error("Transcript generation failed recordingId={} transcriptId={} engine={}",
                    recordingId, transcript.getId(), engine.key(), exception);
            return map(transcript);
        } finally {
            deleteIfExists(sourceVideoPath);
        }
    }

    @Transactional(readOnly = true)
    public String buildSubtitleVtt(UUID recordingId) {
        RecordingTranscript transcript = recordingTranscriptRepository.findByRecording_Id(recordingId)
                .orElseThrow(() -> new NotFoundException("Transcript not found for recording: " + recordingId));

        if (transcript.getStatus() != RecordingTranscriptStatus.READY || transcript.getSegments().isEmpty()) {
            throw new IllegalStateException("Transcript subtitles are not available yet");
        }

        StringBuilder builder = new StringBuilder("WEBVTT\n\n");
        for (RecordingTranscriptSegment segment : transcript.getSegments()) {
            builder.append(segment.getSegmentIndex() + 1).append('\n')
                    .append(formatVttTimestamp(segment.getStartSeconds()))
                    .append(" --> ")
                    .append(formatVttTimestamp(segment.getEndSeconds()))
                    .append('\n')
                    .append(HtmlUtils.htmlEscape(segment.getText()))
                    .append("\n\n");
        }
        return builder.toString();
    }

    private SessionTranscriptResponse aggregateSessionTranscript(UUID sessionId, List<RecordingAsset> recordings) {
        RecordingTranscriptStatus status = RecordingTranscriptStatus.NOT_REQUESTED;
        int readyRecordings = 0;
        int failedRecordings = 0;
        int processingRecordings = 0;
        int pendingRecordings = 0;

        Instant startedAt = null;
        Instant completedAt = null;
        Instant createdAt = null;
        Instant updatedAt = null;
        String engine = null;
        String model = null;
        String languageCode = null;
        List<SessionTranscriptSegmentResponse> aggregateSegments = new ArrayList<>();
        long fallbackOffsetMs = 0L;

        for (RecordingAsset recording : recordings) {
            long recordingOffsetMs = RecordingTimelineSupport.inferredSessionStartMs(recording, fallbackOffsetMs);
            long nextFallbackOffsetMs = RecordingTimelineSupport.inferredSessionEndMs(recording, recordingOffsetMs);
            fallbackOffsetMs = nextFallbackOffsetMs;

            RecordingTranscript transcript = recording.getTranscript();
            if (transcript == null) {
                continue;
            }

            startedAt = earliest(startedAt, transcript.getStartedAt());
            completedAt = latest(completedAt, transcript.getCompletedAt());
            createdAt = earliest(createdAt, transcript.getCreatedAt());
            updatedAt = latest(updatedAt, transcript.getUpdatedAt());
            engine = coalesce(engine, transcript.getEngine());
            model = coalesce(model, transcript.getModel());
            languageCode = coalesce(languageCode, transcript.getLanguageCode());

            switch (transcript.getStatus()) {
                case READY -> {
                    readyRecordings++;
                    aggregateSegments.addAll(transcript.getSegments().stream()
                            .map(segment -> mapSessionSegment(segment, recording, recordingOffsetMs))
                            .toList());
                }
                case FAILED -> failedRecordings++;
                case PROCESSING -> processingRecordings++;
                case PENDING -> pendingRecordings++;
                case NOT_REQUESTED -> {
                }
            }
        }

        if (readyRecordings == recordings.size() && !recordings.isEmpty()) {
            status = RecordingTranscriptStatus.READY;
        } else if (processingRecordings > 0) {
            status = RecordingTranscriptStatus.PROCESSING;
        } else if (pendingRecordings > 0) {
            status = RecordingTranscriptStatus.PENDING;
        } else if (failedRecordings > 0) {
            status = RecordingTranscriptStatus.FAILED;
        } else if (readyRecordings > 0) {
            status = RecordingTranscriptStatus.READY;
        }

        String fullText = aggregateSegments.stream()
                .map(SessionTranscriptSegmentResponse::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining(" "))
                .trim();

        String errorMessage = failedRecordings > 0
                ? failedRecordings + " recording segment transcript(s) failed."
                : null;

        log.info(
                "Aggregated session transcript sessionId={} status={} totalRecordings={} readyRecordings={} failedRecordings={} processingRecordings={} pendingRecordings={} transcriptSegmentCount={}",
                sessionId,
                status,
                recordings.size(),
                readyRecordings,
                failedRecordings,
                processingRecordings,
                pendingRecordings,
                aggregateSegments.size()
        );

        return new SessionTranscriptResponse(
                sessionId,
                status,
                engine,
                model,
                languageCode,
                fullText.isBlank() ? null : fullText,
                errorMessage,
                startedAt,
                completedAt,
                createdAt,
                updatedAt,
                recordings.size(),
                readyRecordings,
                failedRecordings,
                processingRecordings,
                pendingRecordings,
                aggregateSegments
        );
    }

    private SessionTranscriptSegmentResponse mapSessionSegment(
            RecordingTranscriptSegment segment,
            RecordingAsset recording,
            long recordingOffsetMs) {
        BigDecimal offsetSeconds = RecordingTimelineSupport.millisToSeconds(recordingOffsetMs);
        return new SessionTranscriptSegmentResponse(
                segment.getId(),
                recording.getId(),
                RecordingTimelineSupport.segmentSequence(recording),
                segment.getSegmentIndex(),
                offsetSeconds.add(segment.getStartSeconds()),
                offsetSeconds.add(segment.getEndSeconds()),
                segment.getText(),
                segment.getConfidence()
        );
    }

    private List<RecordingAsset> orderedSessionRecordings(UUID sessionId) {
        liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));

        return recordingAssetRepository.findBySession_IdOrderByCreatedAtAsc(sessionId).stream()
                .sorted(RecordingTimelineSupport.segmentTimelineComparator())
                .toList();
    }

    private Map<String, RecordingTranscriptEngine> indexEngines(List<RecordingTranscriptEngine> engines) {
        Map<String, RecordingTranscriptEngine> indexed = new LinkedHashMap<>();
        for (RecordingTranscriptEngine engine : engines) {
            indexed.put(engine.key().toLowerCase(Locale.ROOT), engine);
        }
        return indexed;
    }

    private RecordingTranscriptEngine requiredEngine() {
        String configuredEngine = appProperties.transcript().engine();
        if (configuredEngine == null || configuredEngine.isBlank()) {
            throw new IllegalStateException("Transcript engine is not configured");
        }

        RecordingTranscriptEngine engine = transcriptEngines.get(configuredEngine.toLowerCase(Locale.ROOT));
        if (engine == null) {
            throw new IllegalStateException("Unsupported transcript engine configured: " + configuredEngine);
        }
        return engine;
    }

    private RecordingTranscriptResponse notRequested(RecordingAsset recording) {
        log.info("Transcript not yet requested for recordingId={}", recording.getId());
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
                List.of());
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

    private Path downloadRecordingToTempFile(RecordingAsset recording) throws IOException {
        String extension = extensionFor(recording.getObjectKey());
        Path tempFile = Files.createTempFile("bodycam-recording-" + recording.getId(), extension);
        try (InputStream inputStream = objectStorageService.download(recording.getObjectKey())) {
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private List<RecordingTranscriptSegment> toSegments(
            RecordingTranscript transcript,
            List<TranscriptSegmentPayload> payloads,
            Instant createdAt) {
        List<RecordingTranscriptSegment> segments = new ArrayList<>();
        for (int index = 0; index < payloads.size(); index++) {
            TranscriptSegmentPayload payload = payloads.get(index);
            RecordingTranscriptSegment segment = new RecordingTranscriptSegment();
            segment.setId(UUID.randomUUID());
            segment.setTranscript(transcript);
            segment.setSegmentIndex(index);
            segment.setStartSeconds(payload.startSeconds());
            segment.setEndSeconds(payload.endSeconds());
            segment.setText(payload.text());
            segment.setConfidence(payload.confidence());
            segment.setCreatedAt(createdAt);
            segments.add(segment);
        }
        return segments;
    }

    private String extensionFor(String objectKey) {
        int dotIndex = objectKey.lastIndexOf('.');
        return dotIndex >= 0 ? objectKey.substring(dotIndex) : ".bin";
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary transcript file {}", path, exception);
        }
    }

    private String formatVttTimestamp(BigDecimal secondsValue) {
        long millis = secondsValue.multiply(BigDecimal.valueOf(1000)).longValue();
        long hours = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long milliseconds = millis % 1000;
        return "%02d:%02d:%02d.%03d".formatted(hours, minutes, seconds, milliseconds);
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
                transcript.getSegments().stream().map(this::mapSegment).toList());
    }

    private RecordingTranscriptSegmentResponse mapSegment(RecordingTranscriptSegment segment) {
        return new RecordingTranscriptSegmentResponse(
                segment.getId(),
                segment.getSegmentIndex(),
                segment.getStartSeconds(),
                segment.getEndSeconds(),
                segment.getText(),
                segment.getConfidence());
    }

    private Instant earliest(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isBefore(current) ? candidate : current;
    }

    private Instant latest(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return candidate.isAfter(current) ? candidate : current;
    }

    private String coalesce(String current, String candidate) {
        return current != null && !current.isBlank() ? current : candidate;
    }
}
