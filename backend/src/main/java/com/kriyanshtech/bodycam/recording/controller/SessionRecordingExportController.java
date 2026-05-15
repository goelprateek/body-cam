package com.kriyanshtech.bodycam.recording.controller;

import com.kriyanshtech.bodycam.recording.dto.SessionRecordingExportResponse;
import com.kriyanshtech.bodycam.recording.service.SessionRecordingExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/recordings/export-package")
public class SessionRecordingExportController {
    private static final Logger log = LoggerFactory.getLogger(SessionRecordingExportController.class);
    private final SessionRecordingExportService exportService;

    public SessionRecordingExportController(SessionRecordingExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<SessionRecordingExportResponse> getLatestExport(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received latest session export request sessionId={}", sessionId);
        return ResponseEntity.ok(exportService.getLatestExport(sessionId));
    }

    @PostMapping
    public ResponseEntity<SessionRecordingExportResponse> requestExport(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received session export request sessionId={}", sessionId);
        return ResponseEntity.ok(exportService.requestExport(sessionId));
    }
}
