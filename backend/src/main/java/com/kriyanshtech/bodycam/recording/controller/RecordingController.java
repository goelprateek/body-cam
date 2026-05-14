package com.kriyanshtech.bodycam.recording.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.kriyanshtech.bodycam.recording.dto.CreateRecordingRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingMetadataRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingPlaybackResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingResponse;
import com.kriyanshtech.bodycam.recording.service.RecordingService;

import java.util.UUID;

@RestController
@Validated
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
    public ResponseEntity<com.kriyanshtech.bodycam.common.CursorPageResponse<RecordingResponse>> listRecordings(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(200) int size) {
        log.info("Received request to list recordings cursor={} size={}", cursor != null ? "[HIDDEN]" : "null", size);
        return ResponseEntity.ok(recordingService.listRecordingsCursor(cursor, size));
    }

    @GetMapping("/{recordingId}/playback-url")
    public ResponseEntity<RecordingPlaybackResponse> playbackUrl(@PathVariable("recordingId") UUID recordingId) {
        log.info("Received request to create recording playback URL recordingId={}", recordingId);
        return ResponseEntity.ok(recordingService.playbackUrl(recordingId));
    }

    @PostMapping
    public ResponseEntity<RecordingResponse> createRecording(@Valid @RequestBody CreateRecordingRequest request) {
        log.info(
                "Received create recording request sessionId={} objectKey={} durationSeconds={} metadataPresent={}",
                request.sessionId(),
                request.objectKey(),
                request.durationSeconds(),
                request.metadata() != null);
        return ResponseEntity.ok(recordingService.createRecording(request));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecordingResponse> uploadRecording(
            @RequestParam("sessionId") String sessionIdValue,
            @RequestParam(value = "durationSeconds", required = false) String durationSecondsValue,
            @RequestParam(value = "metadata", required = false) String metadataJson,
            @RequestParam("file") MultipartFile file) {
        UUID sessionId = parseSessionId(sessionIdValue);
        Integer durationSeconds = parseDurationSeconds(durationSecondsValue);
        log.info(
                "Received recording upload request sessionId={} durationSeconds={} originalFilename={} sizeBytes={} metadataPartPresent={}",
                sessionId,
                durationSeconds,
                file.getOriginalFilename(),
                file.getSize(),
                metadataJson != null && !metadataJson.isBlank());
        RecordingMetadataRequest metadata = parseMetadata(metadataJson);
        return ResponseEntity.ok(recordingService.uploadRecording(sessionId, durationSeconds, metadata, file));
    }

    private UUID parseSessionId(String sessionIdValue) {
        String normalized = normalizePartValue(sessionIdValue);
        if (normalized == null) {
            throw new IllegalArgumentException("Recording upload sessionId is required");
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid recording upload sessionId: " + normalized, exception);
        }
    }

    private Integer parseDurationSeconds(String durationSecondsValue) {
        String normalized = normalizePartValue(durationSecondsValue);
        if (normalized == null) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid recording upload durationSeconds: " + normalized, exception);
        }
    }

    private String normalizePartValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private RecordingMetadataRequest parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            if (node.isTextual()) {
                log.info("Parsed textual recording metadata payload");
                return objectMapper.readValue(node.asText(), RecordingMetadataRequest.class);
            }
            log.info("Parsed structured recording metadata payload");
            return objectMapper.treeToValue(node, RecordingMetadataRequest.class);
        } catch (Exception exception) {
            log.warn("Failed to parse recording metadata JSON payload. Proceeding without metadata.", exception);
            return null;
        }
    }
}
