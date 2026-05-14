package com.kriyanshtech.bodycam.recording.transcript;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TranscriptSummaryService {
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^a-zA-Z0-9']+");
    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "that", "with", "from", "this", "have", "were", "been", "into",
            "they", "their", "there", "about", "would", "could", "should", "while", "where",
            "when", "what", "which", "will", "shall", "your", "ours", "after", "before",
            "during", "through", "over", "under", "again", "further", "once", "here",
            "because", "than", "then", "them", "some", "such", "only", "very", "just",
            "also", "much", "more", "most", "each", "other", "same", "does", "did",
            "doing", "done", "having", "being", "within", "without", "across", "onto",
            "upon", "clip", "session", "recording", "transcript");

    public SessionTranscriptSummary summarize(String fullText, int recordingCount, int segmentCount) {
        String normalized = fullText == null ? "" : fullText.trim();
        if (normalized.isBlank()) {
            return new SessionTranscriptSummary(null, null, List.of());
        }

        List<String> sentences = sentences(normalized);
        List<String> keywords = keywords(normalized);
        String shortSummary = buildShortSummary(sentences);
        String incidentSummary = buildIncidentSummary(recordingCount, segmentCount, keywords, shortSummary);
        return new SessionTranscriptSummary(shortSummary, incidentSummary, keywords);
    }

    private List<String> sentences(String fullText) {
        String[] parts = SENTENCE_SPLIT.split(fullText);
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (!trimmed.isBlank()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<String> keywords(String fullText) {
        String[] parts = WORD_SPLIT.split(fullText.toLowerCase(Locale.ROOT));
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String part : parts) {
            if (part == null || part.isBlank() || part.length() < 4 || STOP_WORDS.contains(part)) {
                continue;
            }
            counts.merge(part, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String buildShortSummary(List<String> sentences) {
        if (sentences.isEmpty()) {
            return null;
        }
        String summary = sentences.stream()
                .limit(2)
                .reduce((left, right) -> left + " " + right)
                .orElse(sentences.get(0))
                .trim();
        return summary.length() > 320 ? summary.substring(0, 317).trim() + "..." : summary;
    }

    private String buildIncidentSummary(int recordingCount, int segmentCount, List<String> keywords, String shortSummary) {
        StringBuilder builder = new StringBuilder();
        builder.append("Session coverage spans ")
                .append(recordingCount)
                .append(recordingCount == 1 ? " recording" : " recordings")
                .append(" and ")
                .append(segmentCount)
                .append(segmentCount == 1 ? " transcript segment" : " transcript segments")
                .append('.');
        if (!keywords.isEmpty()) {
            builder.append(" Key topics: ")
                    .append(String.join(", ", keywords))
                    .append('.');
        } else if (shortSummary != null && !shortSummary.isBlank()) {
            builder.append(" Review summary: ")
                    .append(shortSummary);
        }
        return builder.toString();
    }
}
