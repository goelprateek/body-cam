package com.kriyanshtech.bodycam.recording.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.recording.dto.SessionRecordingExportResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionRecordingTimelineResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.SessionRecordingExport;
import com.kriyanshtech.bodycam.recording.entity.SessionRecordingExportStatus;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.SessionRecordingExportRepository;
import com.kriyanshtech.bodycam.session.entity.LiveSession;
import com.kriyanshtech.bodycam.session.repository.LiveSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SessionRecordingExportService {
    private static final Logger log = LoggerFactory.getLogger(SessionRecordingExportService.class);
    private static final int EXPORT_DOWNLOAD_URL_EXPIRY_SECONDS = 300;

    private final SessionRecordingExportRepository exportRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final RecordingAssetRepository recordingAssetRepository;
    private final RecordingService recordingService;
    private final RecordingTranscriptService recordingTranscriptService;
    private final ObjectStorageService objectStorageService;
    private final ObjectMapper objectMapper;

    public SessionRecordingExportService(
            SessionRecordingExportRepository exportRepository,
            LiveSessionRepository liveSessionRepository,
            RecordingAssetRepository recordingAssetRepository,
            RecordingService recordingService,
            RecordingTranscriptService recordingTranscriptService,
            ObjectStorageService objectStorageService,
            ObjectMapper objectMapper) {
        this.exportRepository = exportRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingService = recordingService;
        this.recordingTranscriptService = recordingTranscriptService;
        this.objectStorageService = objectStorageService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SessionRecordingExportResponse getLatestExport(UUID sessionId) {
        liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        return exportRepository.findTopBySession_IdOrderByCreatedAtDesc(sessionId)
                .map(this::map)
                .orElseGet(() -> notRequested(sessionId));
    }

    @Transactional
    public SessionRecordingExportResponse requestExport(UUID sessionId) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        SessionRecordingExport existing = exportRepository.findTopBySession_IdOrderByCreatedAtDesc(sessionId).orElse(null);
        if (existing != null && (existing.getStatus() == SessionRecordingExportStatus.PENDING
                || existing.getStatus() == SessionRecordingExportStatus.PROCESSING
                || existing.getStatus() == SessionRecordingExportStatus.READY)) {
            log.info("Reusing existing session recording export sessionId={} exportId={} status={}", sessionId, existing.getId(), existing.getStatus());
            return map(existing);
        }

        Instant now = Instant.now();
        SessionRecordingExport export = new SessionRecordingExport();
        export.setId(UUID.randomUUID());
        export.setSession(session);
        export.setStatus(SessionRecordingExportStatus.PENDING);
        export.setCreatedAt(now);
        export.setUpdatedAt(now);
        export.setArtifactCount(0);
        SessionRecordingExport savedExport = exportRepository.save(export);
        log.info("Queued session recording export sessionId={} exportId={}", sessionId, savedExport.getId());
        return map(savedExport);
    }

    @Transactional
    public int processPendingExports() {
        int processedCount = 0;
        while (true) {
            UUID exportId = claimNextPendingExport();
            if (exportId == null) {
                break;
            }
            processClaimedExport(exportId);
            processedCount++;
        }
        return processedCount;
    }

    @Transactional
    protected UUID claimNextPendingExport() {
        SessionRecordingExport export = exportRepository.findFirstByStatusOrderByCreatedAtAsc(SessionRecordingExportStatus.PENDING)
                .orElse(null);
        if (export == null) {
            return null;
        }

        Instant now = Instant.now();
        export.setStatus(SessionRecordingExportStatus.PROCESSING);
        export.setStartedAt(now);
        export.setCompletedAt(null);
        export.setErrorMessage(null);
        export.setUpdatedAt(now);
        exportRepository.save(export);
        log.info("Claimed session export job sessionId={} exportId={}", export.getSession().getId(), export.getId());
        return export.getId();
    }

    private void processClaimedExport(UUID exportId) {
        SessionRecordingExport export = exportRepository.findById(exportId)
                .orElseThrow(() -> new NotFoundException("Session recording export not found: " + exportId));
        if (export.getStatus() != SessionRecordingExportStatus.PROCESSING) {
            log.info("Skipping session export processing because job is no longer claimable sessionId={} exportId={} status={}",
                    export.getSession().getId(), exportId, export.getStatus());
            return;
        }

        UUID sessionId = export.getSession().getId();
        Path tempZipPath = null;
        try {
            log.info("Session recording export started sessionId={} exportId={}", sessionId, exportId);
            SessionRecordingTimelineResponse timeline = recordingService.sessionTimeline(sessionId);
            SessionTranscriptResponse transcript = recordingTranscriptService.getSessionTranscript(sessionId);
            List<RecordingAsset> recordings = recordingAssetRepository.findActiveBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                    .sorted(RecordingTimelineSupport.segmentTimelineComparator())
                    .toList();

            tempZipPath = Files.createTempFile("bodycam-export-" + sessionId + "-", ".zip");
            int artifactCount = buildExportZip(tempZipPath, export.getSession(), recordings, timeline, transcript);
            String objectKey = "exports/sessions/%s/%s.zip".formatted(sessionId, exportId);

            try (InputStream inputStream = Files.newInputStream(tempZipPath)) {
                objectStorageService.upload(objectKey, inputStream, Files.size(tempZipPath), "application/zip");
            }

            export.setStatus(SessionRecordingExportStatus.READY);
            export.setObjectKey(objectKey);
            export.setPackageSizeBytes(Files.size(tempZipPath));
            export.setArtifactCount(artifactCount);
            export.setCompletedAt(Instant.now());
            export.setUpdatedAt(Instant.now());
            exportRepository.save(export);
            log.info("Session recording export completed sessionId={} exportId={} objectKey={} packageSizeBytes={} artifactCount={}",
                    sessionId, exportId, objectKey, export.getPackageSizeBytes(), artifactCount);
        } catch (Exception exception) {
            export.setStatus(SessionRecordingExportStatus.FAILED);
            export.setErrorMessage(exception.getMessage());
            export.setCompletedAt(Instant.now());
            export.setUpdatedAt(Instant.now());
            exportRepository.save(export);
            log.error("Session recording export failed sessionId={} exportId={}", sessionId, exportId, exception);
        } finally {
            if (tempZipPath != null) {
                try {
                    Files.deleteIfExists(tempZipPath);
                } catch (IOException exception) {
                    log.warn("Failed to delete temporary export file {}", tempZipPath, exception);
                }
            }
        }
    }

    private int buildExportZip(
            Path zipPath,
            LiveSession session,
            List<RecordingAsset> recordings,
            SessionRecordingTimelineResponse timeline,
            SessionTranscriptResponse transcript) throws IOException {
        Map<String, Object> sessionSummary = new LinkedHashMap<>();
        sessionSummary.put("sessionId", session.getId());
        sessionSummary.put("workerId", session.getWorkerId());
        sessionSummary.put("workerName", session.getWorkerName());
        sessionSummary.put("referenceNumber", session.getReferenceNumber());
        sessionSummary.put("roomName", session.getRoomName());
        sessionSummary.put("status", session.getStatus());
        sessionSummary.put("startedAt", session.getStartedAt());
        sessionSummary.put("endedAt", session.getEndedAt());
        sessionSummary.put("createdAt", session.getCreatedAt());

        Map<String, Object> recordingSummary = new LinkedHashMap<>();
        recordingSummary.put("count", recordings.size());
        recordingSummary.put("recordings", recordings.stream().map(recording -> {
            Map<String, Object> recordingMap = new LinkedHashMap<>();
            recordingMap.put("recordingId", recording.getId());
            recordingMap.put("objectKey", recording.getObjectKey());
            recordingMap.put("durationSeconds", recording.getDurationSeconds());
            recordingMap.put("createdAt", recording.getCreatedAt());
            recordingMap.put("segmentSequence", RecordingTimelineSupport.segmentSequence(recording));
            recordingMap.put("capturedAt", recording.getMetadata() != null ? recording.getMetadata().getCapturedAt() : null);
            recordingMap.put("sessionElapsedStartMs", recording.getMetadata() != null ? recording.getMetadata().getSessionElapsedStartMs() : null);
            recordingMap.put("sessionElapsedEndMs", recording.getMetadata() != null ? recording.getMetadata().getSessionElapsedEndMs() : null);
            return recordingMap;
        }).toList());

        int artifactCount = 0;
        try (OutputStream outputStream = Files.newOutputStream(zipPath);
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            artifactCount += writeZipEntry(zipOutputStream, "session-summary.json", objectMapper.writeValueAsBytes(sessionSummary));
            artifactCount += writeZipEntry(zipOutputStream, "recording-timeline.json", objectMapper.writeValueAsBytes(timeline));
            artifactCount += writeZipEntry(zipOutputStream, "recordings.json", objectMapper.writeValueAsBytes(recordingSummary));
            artifactCount += writeZipEntry(zipOutputStream, "session-transcript.json", objectMapper.writeValueAsBytes(transcript));

            String transcriptText = transcript.fullText() == null ? "" : transcript.fullText();
            artifactCount += writeZipEntry(zipOutputStream, "session-transcript.txt", transcriptText.getBytes(StandardCharsets.UTF_8));

            if (!transcript.segments().isEmpty()) {
                artifactCount += writeZipEntry(zipOutputStream, "session-subtitles.vtt",
                        recordingTranscriptService.buildSessionSubtitleVtt(session.getId()).getBytes(StandardCharsets.UTF_8));
            }
        }
        return artifactCount;
    }

    private int writeZipEntry(ZipOutputStream zipOutputStream, String filename, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(filename);
        zipOutputStream.putNextEntry(entry);
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
        return 1;
    }

    private SessionRecordingExportResponse map(SessionRecordingExport export) {
        String downloadUrl = null;
        Integer expiresInSeconds = null;
        if (export.getStatus() == SessionRecordingExportStatus.READY && export.getObjectKey() != null) {
            downloadUrl = objectStorageService.presignedDownloadUrl(export.getObjectKey(), EXPORT_DOWNLOAD_URL_EXPIRY_SECONDS);
            expiresInSeconds = EXPORT_DOWNLOAD_URL_EXPIRY_SECONDS;
        }
        return new SessionRecordingExportResponse(
                export.getId(),
                export.getSession().getId(),
                export.getStatus(),
                export.getObjectKey(),
                downloadUrl,
                expiresInSeconds,
                export.getPackageSizeBytes(),
                export.getArtifactCount(),
                export.getErrorMessage(),
                export.getStartedAt(),
                export.getCompletedAt(),
                export.getCreatedAt(),
                export.getUpdatedAt()
        );
    }

    private SessionRecordingExportResponse notRequested(UUID sessionId) {
        return new SessionRecordingExportResponse(
                null,
                sessionId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
