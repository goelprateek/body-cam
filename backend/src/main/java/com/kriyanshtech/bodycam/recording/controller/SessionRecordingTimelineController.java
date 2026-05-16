package com.kriyanshtech.bodycam.recording.controller;

import com.kriyanshtech.bodycam.recording.dto.SessionRecordingTimelineResponse;
import com.kriyanshtech.bodycam.recording.service.RecordingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/recordings")
public class SessionRecordingTimelineController {
    private static final Logger log = LoggerFactory.getLogger(SessionRecordingTimelineController.class);

    private final RecordingService recordingService;

    public SessionRecordingTimelineController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @GetMapping("/timeline")
    public ResponseEntity<SessionRecordingTimelineResponse> timeline(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received session recording timeline request sessionId={}", sessionId);
        return ResponseEntity.ok(recordingService.sessionTimeline(sessionId));
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivateSessionRecordings(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received request to soft delete session recordings sessionId={}", sessionId);
        recordingService.deactivateSessionRecordings(sessionId);
        return ResponseEntity.noContent().build();
    }
}
