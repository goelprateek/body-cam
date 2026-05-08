package com.company.bodycam.recording.service;

import com.company.bodycam.common.NotFoundException;
import com.company.bodycam.recording.dto.CreateRecordingRequest;
import com.company.bodycam.recording.dto.RecordingResponse;
import com.company.bodycam.recording.entity.RecordingAsset;
import com.company.bodycam.recording.repository.RecordingAssetRepository;
import com.company.bodycam.session.entity.LiveSession;
import com.company.bodycam.session.repository.LiveSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RecordingService {

    private final RecordingAssetRepository recordingAssetRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final ObjectStorageService objectStorageService;

    public RecordingService(
            RecordingAssetRepository recordingAssetRepository,
            LiveSessionRepository liveSessionRepository,
            ObjectStorageService objectStorageService
    ) {
        this.recordingAssetRepository = recordingAssetRepository;
        this.liveSessionRepository = liveSessionRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional(readOnly = true)
    public List<RecordingResponse> listRecordings() {
        return recordingAssetRepository.findAllByOrderByCreatedAtDesc().stream().map(this::map).toList();
    }

    @Transactional
    public RecordingResponse createRecording(CreateRecordingRequest request) {
        LiveSession session = liveSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new NotFoundException("Session not found: " + request.sessionId()));

        return saveRecording(
                session,
                request.objectKey(),
                request.playbackUrl(),
                request.durationSeconds()
        );
    }

    @Transactional
    public RecordingResponse uploadRecording(UUID sessionId, Integer durationSeconds, MultipartFile file) {
        LiveSession session = liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Recording segment file is required");
        }

        String extension = extensionFor(file.getOriginalFilename(), file.getContentType());
        String objectKey = "sessions/%s/%s%s".formatted(sessionId, UUID.randomUUID(), extension);

        try (var inputStream = file.getInputStream()) {
            objectStorageService.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    file.getContentType() == null ? "application/octet-stream" : file.getContentType()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read recording segment", exception);
        }

        return saveRecording(
                session,
                objectKey,
                objectStorageService.playbackUrl(objectKey),
                durationSeconds
        );
    }

    private RecordingResponse saveRecording(
            LiveSession session,
            String objectKey,
            String playbackUrl,
            Integer durationSeconds
    ) {
        RecordingAsset recordingAsset = new RecordingAsset();
        recordingAsset.setId(UUID.randomUUID());
        recordingAsset.setSession(session);
        recordingAsset.setObjectKey(objectKey);
        recordingAsset.setPlaybackUrl(playbackUrl);
        recordingAsset.setDurationSeconds(durationSeconds);
        recordingAsset.setCreatedAt(Instant.now());

        return map(recordingAssetRepository.save(recordingAsset));
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
                asset.getPlaybackUrl(),
                asset.getDurationSeconds(),
                asset.getCreatedAt()
        );
    }
}
