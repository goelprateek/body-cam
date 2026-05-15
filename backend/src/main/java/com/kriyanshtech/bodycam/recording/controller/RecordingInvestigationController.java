package com.kriyanshtech.bodycam.recording.controller;

import com.kriyanshtech.bodycam.recording.dto.RecordingInvestigationSearchResponse;
import com.kriyanshtech.bodycam.recording.service.RecordingInvestigationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recordings/investigation-search")
public class RecordingInvestigationController {
    private static final Logger log = LoggerFactory.getLogger(RecordingInvestigationController.class);
    private final RecordingInvestigationService investigationService;

    public RecordingInvestigationController(RecordingInvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @GetMapping
    public ResponseEntity<RecordingInvestigationSearchResponse> search(@RequestParam("q") String query) {
        log.info("Received investigation search request queryLength={}", query.length());
        return ResponseEntity.ok(investigationService.search(query));
    }
}
