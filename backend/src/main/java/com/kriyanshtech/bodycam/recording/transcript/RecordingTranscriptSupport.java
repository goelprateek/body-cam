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
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("Failed to delete temporary transcript {} file {}", label, path, exception);
        }
    }
}
