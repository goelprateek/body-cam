package com.kriyanshtech.bodycam.recording.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.kriyanshtech.bodycam.recording.dto.CreateRecordingRequest;
import com.kriyanshtech.bodycam.recording.dto.RecordingPlaybackResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingResponse;
import com.kriyanshtech.bodycam.recording.service.RecordingService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recordings")
public class RecordingController {

    private final RecordingService recordingService;

    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
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
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(recordingService.uploadRecording(sessionId, durationSeconds, file));
    }
}
