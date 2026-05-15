package com.kriyanshtech.bodycam.recording.transcript;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class PunctuationRestorationService {
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    public List<TranscriptSegmentPayload> restore(List<TranscriptSegmentPayload> segments) {
        List<TranscriptSegmentPayload> restored = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return restored;
        }

        for (TranscriptSegmentPayload segment : segments) {
            String normalized = normalizeText(segment.text());
            if (normalized.isBlank()) {
                continue;
            }
            restored.add(new TranscriptSegmentPayload(
                    segment.startSeconds(),
                    segment.endSeconds(),
                    punctuate(normalized),
                    segment.confidence()));
        }
        return restored;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\t]]", " ");
        normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ").trim();
        if (normalized.isBlank()) {
            return "";
        }
        return capitalize(normalized);
    }

    private String punctuate(String text) {
        if (text.isBlank()) {
            return text;
        }
        String trimmed = text.trim();
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
            return trimmed;
        }
        return trimmed + ".";
    }

    private String capitalize(String text) {
        if (text.isBlank()) {
            return text;
        }
        char firstChar = text.charAt(0);
        return (Character.toUpperCase(firstChar) + text.substring(1))
                .replaceAll("\\bi\\b", "I");
    }
}
