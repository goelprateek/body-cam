package com.kriyanshtech.bodycam.recording.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionRecordingExportScheduler {
    private static final Logger log = LoggerFactory.getLogger(SessionRecordingExportScheduler.class);

    private final SessionRecordingExportService exportService;

    public SessionRecordingExportScheduler(SessionRecordingExportService exportService) {
        this.exportService = exportService;
    }

    @Scheduled(fixedDelayString = "${app.recording-export.scheduler-delay-ms:5000}")
    public void processPendingExports() {
        int processedCount = exportService.processPendingExports();
        if (processedCount > 0) {
            log.info("Processed {} pending session recording export job(s)", processedCount);
        }
    }
}
