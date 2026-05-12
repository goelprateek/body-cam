package com.kriyanshtech.bodycam.recording.controller;

import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptResponse;
import com.kriyanshtech.bodycam.recording.dto.SessionTranscriptSearchResponse;
import com.kriyanshtech.bodycam.recording.service.RecordingTranscriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/transcript")
public class SessionRecordingTranscriptController {
    private static final Logger log = LoggerFactory.getLogger(SessionRecordingTranscriptController.class);

    private final RecordingTranscriptService recordingTranscriptService;

    public SessionRecordingTranscriptController(RecordingTranscriptService recordingTranscriptService) {
        this.recordingTranscriptService = recordingTranscriptService;
    }

    @GetMapping
    public ResponseEntity<SessionTranscriptResponse> getTranscript(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received session transcript fetch request sessionId={}", sessionId);
        return ResponseEntity.ok(recordingTranscriptService.getSessionTranscript(sessionId));
    }

    @PostMapping("/generate")
    public ResponseEntity<SessionTranscriptResponse> generateTranscript(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received session transcript generation request sessionId={}", sessionId);
        return ResponseEntity.ok(recordingTranscriptService.generateSessionTranscript(sessionId));
    }

    @PostMapping("/retry-failed")
    public ResponseEntity<SessionTranscriptResponse> retryFailedTranscript(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received failed session transcript retry request sessionId={}", sessionId);
        return ResponseEntity.ok(recordingTranscriptService.retryFailedSessionTranscript(sessionId));
    }

    @GetMapping("/search")
    public ResponseEntity<SessionTranscriptSearchResponse> searchTranscript(
            @PathVariable("sessionId") UUID sessionId,
            @RequestParam("q") String query) {
        log.info("Received session transcript search request sessionId={} queryLength={}", sessionId, query.length());
        return ResponseEntity.ok(recordingTranscriptService.searchSessionTranscript(sessionId, query));
    }

    @GetMapping(path = "/subtitles.vtt", produces = "text/vtt")
    public ResponseEntity<String> subtitles(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received session transcript subtitle request sessionId={}", sessionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/vtt"))
                .body(recordingTranscriptService.buildSessionSubtitleVtt(sessionId));
    }
}
