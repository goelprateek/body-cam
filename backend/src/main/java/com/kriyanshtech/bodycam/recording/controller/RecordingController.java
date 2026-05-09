package com.kriyanshtech.bodycam.recording.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.kriyanshtech.bodycam.recording.dto.CreateRecordingRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingMetadataRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingPlaybackResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingResponse;
import com.kriyanshtech.bodycam.recording.service.RecordingService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recordings")
public class RecordingController {
    private static final Logger log = LoggerFactory.getLogger(RecordingController.class);

    private final RecordingService recordingService;
    private final ObjectMapper objectMapper;

    public RecordingController(RecordingService recordingService, ObjectMapper objectMapper) {
        this.recordingService = recordingService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<List<RecordingResponse>> listRecordings() {
        return ResponseEntity.ok(recordingService.listRecordings());
    }

    @GetMapping("/{recordingId}/playback-url")
    public ResponseEntity<RecordingPlaybackResponse> playbackUrl(@PathVariable("recordingId") UUID recordingId) {
        return ResponseEntity.ok(recordingService.playbackUrl(recordingId));
    }

    @PostMapping
    public ResponseEntity<RecordingResponse> createRecording(@Valid @RequestBody CreateRecordingRequest request) {
        return ResponseEntity.ok(recordingService.createRecording(request));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecordingResponse> uploadRecording(
            @RequestParam("sessionId") UUID sessionId,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestPart(value = "metadata", required = false) String metadataJson,
            @RequestParam("file") MultipartFile file
    ) {
        log.info(
                "Received recording upload request sessionId={} durationSeconds={} originalFilename={} sizeBytes={} metadataPartPresent={}",
                sessionId,
                durationSeconds,
                file.getOriginalFilename(),
                file.getSize(),
                metadataJson != null && !metadataJson.isBlank()
        );
        RecordingMetadataRequest metadata = parseMetadata(metadataJson);
        return ResponseEntity.ok(recordingService.uploadRecording(sessionId, durationSeconds, metadata, file));
    }

    private RecordingMetadataRequest parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(metadataJson, RecordingMetadataRequest.class);
        } catch (Exception exception) {
            log.warn("Failed to parse recording metadata JSON payload", exception);
            throw new IllegalArgumentException("Invalid recording metadata payload", exception);
        }
    }
}
