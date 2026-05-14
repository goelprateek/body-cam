package com.kriyanshtech.bodycam.recording.transcript;

import org.slf4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class RecordingTranscriptSupport {
    private RecordingTranscriptSupport() {
    }

    static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    static String joinSegmentText(List<TranscriptSegmentPayload> segments) {
        return segments.stream()
                .map(TranscriptSegmentPayload::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    static String trimMessage(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    static void deleteIfExists(Logger log, Path path, String label) {
        if (path == null) {
            return;
        }

        final int maxAttempts = 5;
        final long baseDelayMs = 250;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException exception) {

                // Last attempt → log warning
                if (attempt == maxAttempts) {
                    log.warn(
                            "Failed to delete temporary transcript {} file {} after {} attempts",
                            label,
                            path,
                            maxAttempts,
                            exception);
                    return;
                }

                // Wait before retry (exponential-ish backoff)
                try {
                    Thread.sleep(baseDelayMs * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();

                    log.warn(
                            "Interrupted while deleting temporary transcript {} file {}",
                            label,
                            path,
                            interruptedException);
                    return;
                }
            }
        }
    }
}
