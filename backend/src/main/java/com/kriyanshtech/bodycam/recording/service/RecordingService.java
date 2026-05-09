package com.kriyanshtech.bodycam.recording.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.recording.dto.CreateRecordingRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingMetadataRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingMetadataResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingPlaybackResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingMetadata;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import com.kriyanshtech.bodycam.recording.repository.RecordingMetadataRepository;
import com.kriyanshtech.bodycam.session.entity.LiveSession;
import com.kriyanshtech.bodycam.session.repository.LiveSessionRepository;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RecordingService {
    private static final int PLAYBACK_URL_EXPIRY_SECONDS = 300;
    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);


    private final RecordingAssetRepository recordingAssetRepository;
    private final RecordingMetadataRepository recordingMetadataRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final ObjectStorageService objectStorageService;

    public RecordingService(
            RecordingAssetRepository recordingAssetRepository,
            RecordingMetadataRepository recordingMetadataRepository,
            LiveSessionRepository liveSessionRepository,
            ObjectStorageService objectStorageService
    ) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.recordingMetadataRepository = recordingMetadataRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> listRecordings() {
        List<RecordingResponse> recordings = recordingAssetRepository.findAllByOrderByCreatedAtDesc().stream().map(this::map).toList();
        log.info("Listed {} recordings", recordings.size());
        return recordings;
    }

    @Transactional(readOnly = true)
    public RecordingPlaybackResponse playbackUrl(UUID recordingId) {
        RecordingAsset asset = recordingAssetRepository.findById(recordingId)
                .orElseThrow(() -> new NotFoundException("Recording not found: " + recordingId));
        log.info("Generating playback URL for recordingId={} objectKey={}", recordingId, asset.getObjectKey());

        return new RecordingPlaybackResponse(
                asset.getId(),
                objectStorageService.presignedPlaybackUrl(asset.getObjectKey(), PLAYBACK_URL_EXPIRY_SECONDS),
                PLAYBACK_URL_EXPIRY_SECONDS
        );
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
                request.metadata() != null
        );

        return saveRecording(
                session,
                request.objectKey(),
                null,
                request.durationSeconds(),
                request.metadata()
        );
    }

    @Transactional
    public RecordingResponse uploadRecording(
            UUID sessionId,
            Integer durationSeconds,
            RecordingMetadataRequest metadata,
            MultipartFile file
    ) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Recording segment file is required");
        }

        String extension = extensionFor(file.getOriginalFilename(), file.getContentType());
        String objectKey = "sessions/%s/%s%s".formatted(sessionId, UUID.randomUUID(), extension);
        log.info(
                "Uploading recording for sessionId={} originalFilename={} sizeBytes={} contentType={} durationSeconds={} metadataPresent={} objectKey={}",
                sessionId,
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType(),
                durationSeconds,
                metadata != null,
                objectKey
        );

        try (var inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType()
            );
        } catch (IOException exception) {
            log.error("Failed to read uploaded recording stream for sessionId={} objectKey={}", sessionId, objectKey, exception);
            throw new IllegalStateException("Failed to read recording segment", exception);
        }

        log.info("Uploaded recording content for sessionId={} objectKey={}", sessionId, objectKey);

        return saveRecording(
                session,
                objectKey,
                null,
                durationSeconds,
                metadata
        );
    }

    private RecordingResponse saveRecording(
            LiveSession session,
            String objectKey,
            String playbackUrl,
            Integer durationSeconds,
            RecordingMetadataRequest metadata
    ) {
        RecordingAsset recordingAsset = new RecordingAsset();
        recordingAsset.setId(UUID.randomUUID());
        recordingAsset.setSession(session);
        recordingAsset.setObjectKey(objectKey);
        recordingAsset.setPlaybackUrl(playbackUrl);
        recordingAsset.setDurationSeconds(durationSeconds);
        recordingAsset.setCreatedAt(Instant.now());
        RecordingAsset savedRecording = recordingAssetRepository.save(recordingAsset);
        log.info(
                "Saved recording asset id={} sessionId={} objectKey={} durationSeconds={}",
                savedRecording.getId(),
                session.getId(),
                objectKey,
                durationSeconds
        );

        if (metadata != null) {
            RecordingMetadata savedMetadata = new RecordingMetadata();
            savedMetadata.setRecording(savedRecording);
            savedMetadata.setCapturedAt(metadata.capturedAt());
            savedMetadata.setLatitude(metadata.latitude());
            savedMetadata.setLongitude(metadata.longitude());
            savedMetadata.setAltitudeMeters(metadata.altitudeMeters());
            savedMetadata.setLocationAccuracyMeters(metadata.locationAccuracyMeters());
            savedMetadata.setCameraFacing(metadata.cameraFacing());
            savedMetadata.setThermalEnabled(metadata.thermalEnabled());
            savedMetadata.setThermalMinC(metadata.thermalMinC());
            savedMetadata.setThermalMaxC(metadata.thermalMaxC());
            savedMetadata.setThermalAvgC(metadata.thermalAvgC());
            savedMetadata.setSensorPayload(metadata.sensorPayload());
            savedMetadata.setCreatedAt(Instant.now());
            savedRecording.setMetadata(recordingMetadataRepository.save(savedMetadata));
            log.info(
                    "Saved recording metadata for recordingId={} capturedAt={} cameraFacing={} hasLocation={} hasSensorPayload={}",
                    savedRecording.getId(),
                    metadata.capturedAt(),
                    metadata.cameraFacing(),
                    metadata.latitude() != null && metadata.longitude() != null,
                    metadata.sensorPayload() != null
            );
        } else {
            log.warn("Recording saved without metadata for recordingId={} sessionId={}", savedRecording.getId(), session.getId());
        }

        return map(savedRecording);
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
                asset.getSession().getRoomName(),
                asset.getObjectKey(),
                null,
                asset.getDurationSeconds(),
                asset.getCreatedAt(),
                mapMetadata(asset.getMetadata())
        );
    }

    private RecordingMetadataResponse mapMetadata(RecordingMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        return new RecordingMetadataResponse(
                metadata.getCapturedAt(),
                metadata.getLatitude(),
                metadata.getLongitude(),
                metadata.getAltitudeMeters(),
                metadata.getLocationAccuracyMeters(),
                metadata.getCameraFacing(),
                metadata.getThermalEnabled(),
                metadata.getThermalMinC(),
                metadata.getThermalMaxC(),
                metadata.getThermalAvgC(),
                metadata.getSensorPayload()
        );
    }
}
