package com.kriyanshtech.bodycam.recording.service;

import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.config.AppProperties;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptSegmentResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptSegment;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.RecordingTranscriptRepository;
import com.kriyanshtech.bodycam.recording.transcript.RecordingTranscriptEngine;
import com.kriyanshtech.bodycam.recording.transcript.RecordingTranscriptGenerationResult;
import com.kriyanshtech.bodycam.recording.transcript.TranscriptSegmentPayload;
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

@Service
public class RecordingTranscriptService {
    private static final Logger log = LoggerFactory.getLogger(RecordingTranscriptService.class);

    private final RecordingAssetRepository recordingAssetRepository;
    private final RecordingTranscriptRepository recordingTranscriptRepository;
    private final ObjectStorageService objectStorageService;
    private final AppProperties appProperties;
    private final Map<String, RecordingTranscriptEngine> transcriptEngines;

    public RecordingTranscriptService(
            RecordingAssetRepository recordingAssetRepository,
            RecordingTranscriptRepository recordingTranscriptRepository,
            ObjectStorageService objectStorageService,
            AppProperties appProperties,
            List<RecordingTranscriptEngine> transcriptEngines) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingTranscriptRepository = recordingTranscriptRepository;
        this.objectStorageService = objectStorageService;
        this.appProperties = appProperties;
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
}
