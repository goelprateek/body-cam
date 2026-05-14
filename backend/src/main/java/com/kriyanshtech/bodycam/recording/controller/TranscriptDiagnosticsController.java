package com.kriyanshtech.bodycam.recording.controller;

import com.kriyanshtech.bodycam.recording.dto.TranscriptSmokeCheckResponse;
import com.kriyanshtech.bodycam.recording.service.TranscriptSmokeCheckService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transcripts")
public class TranscriptDiagnosticsController {
    private final TranscriptSmokeCheckService transcriptSmokeCheckService;

    public TranscriptDiagnosticsController(TranscriptSmokeCheckService transcriptSmokeCheckService) {
        this.transcriptSmokeCheckService = transcriptSmokeCheckService;
    }

    @GetMapping("/smoke-check")
    public ResponseEntity<TranscriptSmokeCheckResponse> smokeCheck() {
        return ResponseEntity.ok(transcriptSmokeCheckService.run());
    }
}
