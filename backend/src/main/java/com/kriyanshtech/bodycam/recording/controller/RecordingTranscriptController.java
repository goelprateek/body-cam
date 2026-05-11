package com.kriyanshtech.bodycam.recording.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kriyanshtech.bodycam.recording.dto.RecordingTranscriptResponse;
import com.kriyanshtech.bodycam.recording.service.RecordingTranscriptService;

import java.util.UUID;

@RestController
@RequestMapping("/api/recordings/{recordingId}/transcript")
public class RecordingTranscriptController {
    private static final Logger log = LoggerFactory.getLogger(RecordingTranscriptController.class);

    private final RecordingTranscriptService recordingTranscriptService;

    public RecordingTranscriptController(RecordingTranscriptService recordingTranscriptService) {
        this.recordingTranscriptService = recordingTranscriptService;
    }

    @GetMapping
    public ResponseEntity<RecordingTranscriptResponse> getTranscript(@PathVariable("recordingId") UUID recordingId) {
        log.info("Received transcript fetch request recordingId={}", recordingId);
        return ResponseEntity.ok(recordingTranscriptService.getTranscript(recordingId));
    }

    @PostMapping("/generate")
    public ResponseEntity<RecordingTranscriptResponse> generateTranscript(
            @PathVariable("recordingId") UUID recordingId) {
        log.info("Received transcript generation request recordingId={}", recordingId);
        return ResponseEntity.ok(recordingTranscriptService.generateTranscript(recordingId));
    }

    @GetMapping(path = "/subtitles.vtt", produces = "text/vtt")
    public ResponseEntity<String> subtitles(@PathVariable("recordingId") UUID recordingId) {
        log.info("Received transcript subtitle request recordingId={}", recordingId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/vtt"))
                .body(recordingTranscriptService.buildSubtitleVtt(recordingId));
    }
}
