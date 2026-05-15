package com.kriyanshtech.bodycam.recording.service;

import com.kriyanshtech.bodycam.common.ConflictException;
import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.config.AppProperties;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptSegmentResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptRecordingResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptSearchResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptSegmentResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptProcessingStage;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptSegment;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.transcript.ProcessedTranscriptResult;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.RecordingTranscriptRepository;
import com.kriyanshtech.bodycam.recording.transcript.RecordingTranscriptEngine;
import com.kriyanshtech.bodycam.recording.transcript.RecordingTranscriptGenerationResult;
import com.kriyanshtech.bodycam.recording.transcript.SessionTranscriptSummary;
import com.kriyanshtech.bodycam.recording.transcript.TranscriptSegmentPayload;
import com.kriyanshtech.bodycam.recording.transcript.TranscriptPostProcessingService;
import com.kriyanshtech.bodycam.recording.transcript.TranscriptRecoveryDecision;
import com.kriyanshtech.bodycam.recording.transcript.TranscriptRecoveryService;
import com.kriyanshtech.bodycam.recording.transcript.TranscriptSummaryService;
import com.kriyanshtech.bodycam.session.repository.LiveSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
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
    private final TranscriptPostProcessingService transcriptPostProcessingService;
    private final TranscriptSummaryService transcriptSummaryService;
    private final TranscriptRecoveryService transcriptRecoveryService;

    public RecordingTranscriptService(
            RecordingAssetRepository recordingAssetRepository,
            RecordingTranscriptRepository recordingTranscriptRepository,
            ObjectStorageService objectStorageService,
            AppProperties appProperties,
            LiveSessionRepository liveSessionRepository,
            TranscriptPostProcessingService transcriptPostProcessingService,
            TranscriptSummaryService transcriptSummaryService,
            TranscriptRecoveryService transcriptRecoveryService,
            List<RecordingTranscriptEngine> transcriptEngines) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingTranscriptRepository = recordingTranscriptRepository;
        this.objectStorageService = objectStorageService;
        this.appProperties = appProperties;
        this.liveSessionRepository = liveSessionRepository;
        this.transcriptPostProcessingService = transcriptPostProcessingService;
        this.transcriptSummaryService = transcriptSummaryService;
        this.transcriptRecoveryService = transcriptRecoveryService;
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
    public RecordingTranscriptResponse generateTranscript(UUID recordingId, String requestedEngine) {
        assertTranscriptEnabled();
        RecordingAsset recording = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));
        RecordingTranscript transcript = enqueueTranscript(recording, true, requestedEngine);
        log.info(
                "Queued transcript generation recordingId={} transcriptId={} status={} engine={} requestedEngine={}",
                recordingId,
                transcript.getId(),
                transcript.getStatus(),
                transcript.getEngine(),
                requestedEngine);
        return map(transcript);
    }

    @Transactional(readOnly = true)
    public SessionTranscriptResponse getSessionTranscript(UUID sessionId) {
        List<RecordingAsset> recordings = orderedSessionRecordings(sessionId);
        return aggregateSessionTranscript(sessionId, recordings);
    }

    @Transactional(readOnly = true)
    public SessionTranscriptResponse summarizeSessionTranscript(UUID sessionId) {
        return getSessionTranscript(sessionId);
    }

    @Transactional
    public SessionTranscriptResponse generateSessionTranscript(UUID sessionId, String requestedEngine) {
        assertTranscriptEnabled();
        List<RecordingAsset> recordings = orderedSessionRecordings(sessionId);
        int queuedCount = 0;
        log.info("Session transcript generation queued sessionId={} recordingCount={} requestedEngine={}", sessionId,
                recordings.size(), requestedEngine);

        for (RecordingAsset recording : recordings) {
            RecordingTranscript transcript = enqueueTranscript(recording, true, requestedEngine);
            if (transcript.getStatus() == RecordingTranscriptStatus.PENDING
                    || transcript.getStatus() == RecordingTranscriptStatus.PROCESSING) {
                queuedCount++;
            }
        }

        log.info("Session transcript generation request completed sessionId={} queuedRecordings={}", sessionId,
                queuedCount);
        return aggregateSessionTranscript(sessionId, orderedSessionRecordings(sessionId));
    }

    @Transactional
    public SessionTranscriptResponse retryFailedSessionTranscript(UUID sessionId, String requestedEngine) {
        assertTranscriptEnabled();
        List<RecordingAsset> recordings = orderedSessionRecordings(sessionId);
        int retriedCount = 0;
        log.info("Retrying failed or missing session transcript work sessionId={} recordingCount={} requestedEngine={}",
                sessionId, recordings.size(), requestedEngine);

        for (RecordingAsset recording : recordings) {
            RecordingTranscript transcript = recording.getTranscript();
            if (transcript == null || transcript.getStatus() == RecordingTranscriptStatus.FAILED) {
                enqueueTranscript(recording, true, requestedEngine);
                retriedCount++;
            }
        }

        log.info("Retry failed session transcript request completed sessionId={} retriedRecordings={}", sessionId,
                retriedCount);
        return aggregateSessionTranscript(sessionId, orderedSessionRecordings(sessionId));
    }

    @Transactional(readOnly = true)
    public SessionTranscriptSearchResponse searchSessionTranscript(UUID sessionId, String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException("Session transcript search query is required");
        }

        SessionTranscriptResponse transcript = getSessionTranscript(sessionId);
        String loweredQuery = normalizedQuery.toLowerCase(Locale.ROOT);
        List<SessionTranscriptSegmentResponse> matches = transcript.segments().stream()
                .filter(segment -> segment.text() != null
                        && segment.text().toLowerCase(Locale.ROOT).contains(loweredQuery))
                .toList();

        log.info(
                "Session transcript search completed sessionId={} queryLength={} totalMatches={} transcriptStatus={}",
                sessionId,
                normalizedQuery.length(),
                matches.size(),
                transcript.status());
        return new SessionTranscriptSearchResponse(
                sessionId,
                normalizedQuery,
                transcript.status(),
                matches.size(),
                matches);
    }

    @Transactional
    public int processPendingTranscripts() {
        if (!appProperties.transcript().enabled()) {
            return 0;
        }

        int processedCount = 0;
        while (true) {
            UUID transcriptId = claimNextPendingTranscript();
            if (transcriptId == null) {
                break;
            }
            processClaimedTranscript(transcriptId);
            processedCount++;
        }
        return processedCount;
    }

    @Transactional(readOnly = true)
    public String buildSessionSubtitleVtt(UUID sessionId) {
        SessionTranscriptResponse transcript = getSessionTranscript(sessionId);
        if (transcript.segments().isEmpty()) {
            throw new ConflictException("Session transcript subtitles are not available yet");
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

    @Transactional(readOnly = true)
    public String buildSubtitleVtt(UUID recordingId) {
        RecordingTranscript transcript = recordingTranscriptRepository.findByRecording_Id(recordingId)
                .orElseThrow(() -> new NotFoundException("Transcript not found for recording: " + recordingId));

        if (transcript.getStatus() != RecordingTranscriptStatus.READY || transcript.getSegments().isEmpty()) {
            throw new ConflictException("Transcript subtitles are not available yet");
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

    @Transactional
    protected UUID claimNextPendingTranscript() {
        RecordingTranscript transcript = recordingTranscriptRepository
                .findFirstByStatusOrderByCreatedAtAsc(RecordingTranscriptStatus.PENDING)
                .orElse(null);
        if (transcript == null) {
            return null;
        }

        Instant now = Instant.now();
        transcript.setStatus(RecordingTranscriptStatus.PROCESSING);
        transcript.setStartedAt(now);
        transcript.setCompletedAt(null);
        transcript.setErrorMessage(null);
        transcript.setProcessingStage(RecordingTranscriptProcessingStage.TRANSCRIBING);
        transcript.setLastStageAt(now);
        transcript.setUpdatedAt(now);
        recordingTranscriptRepository.save(transcript);
        log.info(
                "Claimed transcript job recordingId={} transcriptId={} engine={}",
                transcript.getRecording().getId(),
                transcript.getId(),
                transcript.getEngine());
        return transcript.getId();
    }

    private void processClaimedTranscript(UUID transcriptId) {
        RecordingTranscript transcript = recordingTranscriptRepository.findById(transcriptId)
                .orElseThrow(() -> new NotFoundException("Transcript not found: " + transcriptId));

        if (transcript.getStatus() != RecordingTranscriptStatus.PROCESSING) {
            log.info(
                    "Skipping transcript processing because job is no longer claimable recordingId={} transcriptId={} status={}",
                    transcript.getRecording().getId(),
                    transcriptId,
                    transcript.getStatus());
            return;
        }

        RecordingTranscriptEngine engine = requiredEngine(transcript.getEngine());
        transcript.setEngine(engine.key());
        transcript.setLanguageCode(appProperties.transcript().languageCode());
        transcript.setUpdatedAt(Instant.now());
        recordingTranscriptRepository.save(transcript);

        UUID recordingId = transcript.getRecording().getId();
        Path sourceVideoPath = null;
        log.info(
                "Transcript generation started recordingId={} transcriptId={} objectKey={} engine={}",
                recordingId,
                transcript.getId(),
                transcript.getRecording().getObjectKey(),
                engine.key());

        RecordingTranscriptProcessingStage failureStage = RecordingTranscriptProcessingStage.TRANSCRIBING;
        try {
            sourceVideoPath = downloadRecordingToTempFile(transcript.getRecording());
            RecordingTranscriptGenerationResult result = engine.generate(sourceVideoPath, recordingId,
                    transcript.getId());
            updateProcessingStage(transcript.getId(), RecordingTranscriptProcessingStage.TRANSCRIBED);
            failureStage = RecordingTranscriptProcessingStage.PUNCTUATED;
            List<TranscriptSegmentPayload> punctuatedSegments = transcriptPostProcessingService.punctuate(result);
            updateProcessingStage(transcript.getId(), RecordingTranscriptProcessingStage.PUNCTUATED);
            failureStage = RecordingTranscriptProcessingStage.FINALIZED;
            ProcessedTranscriptResult processedResult = transcriptPostProcessingService
                    .finalizeTranscript(result, punctuatedSegments);

            finalizeTranscriptSuccess(transcript.getId(), result, processedResult);
        } catch (Exception exception) {
            finalizeTranscriptFailure(transcript.getId(), engine.key(), failureStage, exception);
        } finally {
            deleteIfExists(sourceVideoPath);
        }
    }

    @Transactional
    protected void finalizeTranscriptSuccess(
            UUID transcriptId,
            RecordingTranscriptGenerationResult result,
            ProcessedTranscriptResult processedResult) {
        RecordingTranscript transcript = recordingTranscriptRepository.findById(transcriptId)
                .orElseThrow(() -> new NotFoundException("Transcript not found: " + transcriptId));

        transcript.setEngine(result.engine());
        transcript.setModel(result.model());
        transcript.setLanguageCode(result.languageCode());
        transcript.setFullText(processedResult.fullText());
        transcript.replaceSegments(toSegments(transcript, processedResult.segments(), Instant.now()));
        transcript.setStatus(RecordingTranscriptStatus.READY);
        transcript.setProcessingStage(RecordingTranscriptProcessingStage.FINALIZED);
        transcript.setLastErrorStage(null);
        transcript.setRetryCount(0);
        transcript.setErrorMessage(null);
        transcript.setCompletedAt(Instant.now());
        transcript.setLastStageAt(Instant.now());
        transcript.setUpdatedAt(Instant.now());
        RecordingTranscript savedTranscript = recordingTranscriptRepository.save(transcript);
        log.info(
                "Transcript generation completed recordingId={} transcriptId={} engine={} segmentCount={}",
                savedTranscript.getRecording().getId(),
                savedTranscript.getId(),
                result.engine(),
                savedTranscript.getSegments().size());
    }

    @Transactional
    protected void finalizeTranscriptFailure(
            UUID transcriptId,
            String engineKey,
            RecordingTranscriptProcessingStage failureStage,
            Exception exception) {
        RecordingTranscript transcript = recordingTranscriptRepository.findById(transcriptId)
                .orElseThrow(() -> new NotFoundException("Transcript not found: " + transcriptId));

        TranscriptRecoveryDecision decision = transcriptRecoveryService.evaluate(
                transcript,
                failureStage,
                exception,
                appProperties.transcript().maxRetryCount());
        transcript.replaceSegments(List.of());
        transcript.setStatus(decision.requeue() ? RecordingTranscriptStatus.PENDING : RecordingTranscriptStatus.FAILED);
        transcript.setProcessingStage(decision.requeue()
                ? RecordingTranscriptProcessingStage.QUEUED
                : RecordingTranscriptProcessingStage.FAILED);
        transcript.setLastErrorStage(decision.failureStage());
        transcript.setRetryCount(decision.nextRetryCount());
        transcript.setErrorMessage(decision.errorMessage());
        transcript.setCompletedAt(decision.requeue() ? null : Instant.now());
        transcript.setLastStageAt(Instant.now());
        transcript.setUpdatedAt(Instant.now());
        recordingTranscriptRepository.save(transcript);
        if (decision.requeue()) {
            log.warn(
                    "Transcript generation failed but was requeued recordingId={} transcriptId={} engine={} failureStage={} retryCount={}",
                    transcript.getRecording().getId(),
                    transcript.getId(),
                    engineKey,
                    decision.failureStage(),
                    decision.nextRetryCount(),
                    exception);
        } else {
            log.error(
                    "Transcript generation failed recordingId={} transcriptId={} engine={} failureStage={} retryCount={}",
                    transcript.getRecording().getId(),
                    transcript.getId(),
                    engineKey,
                    decision.failureStage(),
                    decision.nextRetryCount(),
                    exception);
        }
    }

    private RecordingTranscript enqueueTranscript(RecordingAsset recording, boolean forceRegeneration, String requestedEngine) {
        RecordingTranscript transcript = recordingTranscriptRepository.findByRecording_Id(recording.getId())
                .orElseGet(() -> createTranscript(recording));

        if (!forceRegeneration && transcript.getStatus() == RecordingTranscriptStatus.READY) {
            return transcript;
        }

        if (transcript.getStatus() == RecordingTranscriptStatus.PROCESSING) {
            log.info("Transcript already processing recordingId={} transcriptId={}", recording.getId(),
                    transcript.getId());
            return transcript;
        }

        Instant now = Instant.now();
        String resolvedEngine = requiredEngine(requestedEngine).key();
        transcript.setStatus(RecordingTranscriptStatus.PENDING);
        transcript.setEngine(resolvedEngine);
        transcript.setModel(null);
        transcript.setLanguageCode(appProperties.transcript().languageCode());
        transcript.setFullText(null);
        transcript.setErrorMessage(null);
        transcript.setProcessingStage(RecordingTranscriptProcessingStage.QUEUED);
        transcript.setLastErrorStage(null);
        transcript.setRetryCount(0);
        transcript.setStartedAt(null);
        transcript.setCompletedAt(null);
        transcript.setLastStageAt(now);
        transcript.setUpdatedAt(now);
        transcript.replaceSegments(List.of());
        return recordingTranscriptRepository.save(transcript);
    }

    private SessionTranscriptResponse aggregateSessionTranscript(UUID sessionId, List<RecordingAsset> recordings) {
        RecordingTranscriptStatus status = RecordingTranscriptStatus.NOT_REQUESTED;
        int readyRecordings = 0;
        int failedRecordings = 0;
        int processingRecordings = 0;
        int pendingRecordings = 0;
        int notRequestedRecordings = 0;

        Instant startedAt = null;
        Instant completedAt = null;
        Instant createdAt = null;
        Instant updatedAt = null;
        String engine = null;
        String model = null;
        String languageCode = null;
        List<SessionTranscriptSegmentResponse> aggregateSegments = new ArrayList<>();
        List<SessionTranscriptRecordingResponse> aggregateRecordings = new ArrayList<>();
        long fallbackOffsetMs = 0L;

        for (RecordingAsset recording : recordings) {
            long recordingOffsetMs = RecordingTimelineSupport.inferredSessionStartMs(recording, fallbackOffsetMs);
            long nextFallbackOffsetMs = RecordingTimelineSupport.inferredSessionEndMs(recording, recordingOffsetMs);
            fallbackOffsetMs = nextFallbackOffsetMs;

            RecordingTranscript transcript = recording.getTranscript();
            if (transcript == null) {
                notRequestedRecordings++;
                aggregateRecordings.add(mapSessionRecording(recording, null, recordingOffsetMs, nextFallbackOffsetMs));
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
                    aggregateRecordings
                            .add(mapSessionRecording(recording, transcript, recordingOffsetMs, nextFallbackOffsetMs));
                    aggregateSegments.addAll(transcript.getSegments().stream()
                            .map(segment -> mapSessionSegment(segment, recording, recordingOffsetMs))
                            .toList());
                }
                case FAILED -> {
                    failedRecordings++;
                    aggregateRecordings
                            .add(mapSessionRecording(recording, transcript, recordingOffsetMs, nextFallbackOffsetMs));
                }
                case PROCESSING -> {
                    processingRecordings++;
                    aggregateRecordings
                            .add(mapSessionRecording(recording, transcript, recordingOffsetMs, nextFallbackOffsetMs));
                }
                case PENDING -> {
                    pendingRecordings++;
                    aggregateRecordings
                            .add(mapSessionRecording(recording, transcript, recordingOffsetMs, nextFallbackOffsetMs));
                }
                case NOT_REQUESTED -> {
                    notRequestedRecordings++;
                    aggregateRecordings
                            .add(mapSessionRecording(recording, transcript, recordingOffsetMs, nextFallbackOffsetMs));
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

        aggregateSegments = transcriptPostProcessingService.finalizeSessionSegments(
                aggregateSegments.stream()
                        .sorted((left, right) -> left.startSeconds().compareTo(right.startSeconds()))
                        .toList());

        String fullText = aggregateSegments.stream()
                .map(SessionTranscriptSegmentResponse::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
        SessionTranscriptSummary summary = transcriptSummaryService.summarize(
                fullText.isBlank() ? null : fullText,
                recordings.size(),
                aggregateSegments.size());

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
                aggregateSegments.size());

        return new SessionTranscriptResponse(
                sessionId,
                status,
                engine,
                model,
                languageCode,
                fullText.isBlank() ? null : fullText,
                summary.shortSummary(),
                summary.incidentSummary(),
                summary.keywords(),
                errorMessage,
                aggregateProcessingStage(recordings),
                aggregateLastErrorStage(recordings),
                aggregateRetryCount(recordings),
                aggregateLastStageAt(recordings),
                startedAt,
                completedAt,
                createdAt,
                updatedAt,
                recordings.size(),
                readyRecordings,
                failedRecordings,
                processingRecordings,
                pendingRecordings,
                notRequestedRecordings,
                aggregateSegments,
                aggregateRecordings);
    }

    private SessionTranscriptRecordingResponse mapSessionRecording(
            RecordingAsset recording,
            RecordingTranscript transcript,
            long recordingOffsetMs,
            long nextFallbackOffsetMs) {
        RecordingTranscriptStatus status = transcript != null ? transcript.getStatus()
                : RecordingTranscriptStatus.NOT_REQUESTED;
        Long sessionElapsedStartMs = recording.getMetadata() != null
                ? recording.getMetadata().getSessionElapsedStartMs()
                : null;
        Long sessionElapsedEndMs = recording.getMetadata() != null ? recording.getMetadata().getSessionElapsedEndMs()
                : null;
        long effectiveStartMs = sessionElapsedStartMs != null ? sessionElapsedStartMs : recordingOffsetMs;
        long effectiveEndMs = sessionElapsedEndMs != null ? sessionElapsedEndMs : nextFallbackOffsetMs;
        return new SessionTranscriptRecordingResponse(
                recording.getId(),
                RecordingTimelineSupport.segmentSequence(recording),
                status,
                transcript != null ? transcript.getErrorMessage() : null,
                transcript != null ? transcript.getProcessingStage() : null,
                transcript != null ? transcript.getLastErrorStage() : null,
                transcript != null ? transcript.getRetryCount() : 0,
                transcript != null ? transcript.getLastStageAt() : null,
                transcript != null ? transcript.getStartedAt() : null,
                transcript != null ? transcript.getCompletedAt() : null,
                transcript != null ? transcript.getCreatedAt() : null,
                transcript != null ? transcript.getUpdatedAt() : null,
                effectiveStartMs,
                effectiveEndMs,
                recording.getDurationSeconds(),
                transcript != null ? transcript.getSegments().size() : 0);
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
                segment.getConfidence());
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

    private RecordingTranscriptEngine requiredEngine(String requestedEngine) {
        String configuredEngine = requestedEngine != null && !requestedEngine.isBlank()
                ? requestedEngine
                : appProperties.transcript().engine();
        if (configuredEngine == null || configuredEngine.isBlank()) {
            throw new IllegalStateException("Transcript engine is not configured");
        }

        RecordingTranscriptEngine engine = transcriptEngines.get(configuredEngine.toLowerCase(Locale.ROOT));
        if (engine == null) {
            throw new IllegalStateException("Unsupported transcript engine configured: " + configuredEngine);
        }
        return engine;
    }

    private void assertTranscriptEnabled() {
        if (!appProperties.transcript().enabled()) {
            throw new IllegalStateException("Transcript generation is disabled in backend configuration");
        }
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
                0,
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
        transcript.setRetryCount(0);
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
                transcript.getProcessingStage(),
                transcript.getLastErrorStage(),
                transcript.getRetryCount(),
                transcript.getLastStageAt(),
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

    private void updateProcessingStage(UUID transcriptId, RecordingTranscriptProcessingStage processingStage) {
        RecordingTranscript transcript = recordingTranscriptRepository.findById(transcriptId)
                .orElseThrow(() -> new NotFoundException("Transcript not found: " + transcriptId));
        transcript.setProcessingStage(processingStage);
        transcript.setLastStageAt(Instant.now());
        transcript.setUpdatedAt(Instant.now());
        recordingTranscriptRepository.save(transcript);
    }

    private RecordingTranscriptProcessingStage aggregateProcessingStage(List<RecordingAsset> recordings) {
        RecordingTranscriptProcessingStage latestStage = null;
        Instant latestStageAt = null;
        for (RecordingAsset recording : recordings) {
            RecordingTranscript transcript = recording.getTranscript();
            if (transcript == null || transcript.getProcessingStage() == null || transcript.getLastStageAt() == null) {
                continue;
            }
            if (latestStageAt == null || transcript.getLastStageAt().isAfter(latestStageAt)) {
                latestStageAt = transcript.getLastStageAt();
                latestStage = transcript.getProcessingStage();
            }
        }
        return latestStage;
    }

    private Instant aggregateLastStageAt(List<RecordingAsset> recordings) {
        Instant latestStageAt = null;
        for (RecordingAsset recording : recordings) {
            RecordingTranscript transcript = recording.getTranscript();
            if (transcript == null) {
                continue;
            }
            latestStageAt = latest(latestStageAt, transcript.getLastStageAt());
        }
        return latestStageAt;
    }

    private RecordingTranscriptProcessingStage aggregateLastErrorStage(List<RecordingAsset> recordings) {
        RecordingTranscriptProcessingStage latestErrorStage = null;
        Instant latestStageAt = null;
        for (RecordingAsset recording : recordings) {
            RecordingTranscript transcript = recording.getTranscript();
            if (transcript == null || transcript.getLastErrorStage() == null || transcript.getLastStageAt() == null) {
                continue;
            }
            if (latestStageAt == null || transcript.getLastStageAt().isAfter(latestStageAt)) {
                latestStageAt = transcript.getLastStageAt();
                latestErrorStage = transcript.getLastErrorStage();
            }
        }
        return latestErrorStage;
    }

    private Integer aggregateRetryCount(List<RecordingAsset> recordings) {
        int totalRetryCount = 0;
        for (RecordingAsset recording : recordings) {
            RecordingTranscript transcript = recording.getTranscript();
            if (transcript == null || transcript.getRetryCount() == null) {
                continue;
            }
            totalRetryCount += transcript.getRetryCount();
        }
        return totalRetryCount;
    }
}
