package com.kriyanshtech.bodycam.recording.service;

import com.kriyanshtech.bodycam.config.AppProperties;
import com.kriyanshtech.bodycam.recording.dto.TranscriptSmokeCheckResponse;
import com.kriyanshtech.bodycam.recording.transcript.RecordingTranscriptEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TranscriptSmokeCheckService {
    private final AppProperties appProperties;
    private final List<RecordingTranscriptEngine> transcriptEngines;

    public TranscriptSmokeCheckService(AppProperties appProperties, List<RecordingTranscriptEngine> transcriptEngines) {
        this.appProperties = appProperties;
        this.transcriptEngines = transcriptEngines;
    }

    public TranscriptSmokeCheckResponse run() {
        List<String> checks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean enabled = appProperties.transcript().enabled();
        checks.add(enabled ? "Transcript generation is enabled." : "Transcript generation is disabled.");

        String configuredEngine = appProperties.transcript().engine();
        boolean engineConfigured = configuredEngine != null && !configuredEngine.isBlank();
        checks.add(engineConfigured
                ? "Transcript engine is configured as " + configuredEngine + "."
                : "Transcript engine is not configured.");

        boolean engineAvailable = engineConfigured && transcriptEngines.stream()
                .anyMatch(engine -> engine.key().equalsIgnoreCase(configuredEngine));
        checks.add(engineAvailable
                ? "Configured transcript engine bean is available."
                : "Configured transcript engine bean is missing.");

        String configuredEndpoint = configuredEndpoint(configuredEngine);
        if (configuredEndpoint != null && !configuredEndpoint.isBlank()) {
            checks.add("Transcript endpoint is configured.");
        } else {
            warnings.add("Transcript endpoint is not configured for the selected engine.");
        }

        if (appProperties.transcript().pollDelayMs() <= 0) {
            warnings.add("Transcript poll delay should be greater than zero.");
        } else {
            checks.add("Transcript poll delay is configured.");
        }

        boolean ready = enabled && engineConfigured && engineAvailable && configuredEndpoint != null && !configuredEndpoint.isBlank();
        return new TranscriptSmokeCheckResponse(
                ready,
                enabled,
                configuredEngine,
                configuredEndpoint,
                appProperties.transcript().pollDelayMs(),
                checks,
                warnings
        );
    }

    private String configuredEndpoint(String configuredEngine) {
        if (configuredEngine == null) {
            return null;
        }
        return switch (configuredEngine.toLowerCase(Locale.ROOT)) {
            case "vosk" -> appProperties.transcript().voskUrl();
            case "faster-whisper" -> appProperties.transcript().fasterWhisper().url();
            default -> null;
        };
    }
}
