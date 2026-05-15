package com.kriyanshtech.bodycam.recording.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecordingTranscriptScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecordingTranscriptScheduler.class);

    private final RecordingTranscriptService recordingTranscriptService;

    public RecordingTranscriptScheduler(RecordingTranscriptService recordingTranscriptService) {
        this.recordingTranscriptService = recordingTranscriptService;
    }

    @Scheduled(fixedDelayString = "${app.transcript.poll-delay-ms:5000}")
    public void processPendingTranscripts() {
        int processedCount = recordingTranscriptService.processPendingTranscripts();
        if (processedCount > 0) {
            log.info("Processed pending transcript jobs count={}", processedCount);
        }
    }
}
