package com.kriyanshtech.bodycam.recording.transcript;

import com.kriyanshtech.bodycam.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TranscriptSummaryService {
    private static final Logger log = LoggerFactory.getLogger(TranscriptSummaryService.class);
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
    private static final int SUMMARY_CACHE_LIMIT = 128;

    private final ChatClient chatClient;
    private final AppProperties appProperties;
    private final Map<String, SessionTranscriptSummary> summaryCache = Collections.synchronizedMap(
            new LinkedHashMap<>(SUMMARY_CACHE_LIMIT, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SessionTranscriptSummary> eldest) {
                    return size() > SUMMARY_CACHE_LIMIT;
                }
            });

    public TranscriptSummaryService(
            AppProperties appProperties,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.appProperties = appProperties;
        ChatClient.Builder chatClientBuilder = chatClientBuilderProvider.getIfAvailable();
        this.chatClient = chatClientBuilder != null ? chatClientBuilder.build() : null;
    }

    public SessionTranscriptSummary summarize(String fullText, int recordingCount, int segmentCount) {
        String normalized = fullText == null ? "" : fullText.trim();
        if (normalized.isBlank()) {
            return new SessionTranscriptSummary(null, null, List.of());
        }

        if (appProperties.transcript().summary().aiEnabled() && chatClient != null) {
            String cacheKey = cacheKey(normalized, recordingCount, segmentCount);
            SessionTranscriptSummary cached = summaryCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            SessionTranscriptSummary aiSummary = summarizeWithAi(normalized, recordingCount, segmentCount);
            if (aiSummary != null) {
                summaryCache.put(cacheKey, aiSummary);
                return aiSummary;
            }
        }

        return summarizeHeuristically(normalized, recordingCount, segmentCount);
    }

    private SessionTranscriptSummary summarizeHeuristically(String normalized, int recordingCount, int segmentCount) {
        List<String> sentences = sentences(normalized);
        List<String> keywords = keywords(normalized);
        String shortSummary = buildShortSummary(sentences);
        String incidentSummary = buildIncidentSummary(recordingCount, segmentCount, keywords, shortSummary);
        return new SessionTranscriptSummary(shortSummary, incidentSummary, keywords);
    }

    private SessionTranscriptSummary summarizeWithAi(String fullText, int recordingCount, int segmentCount) {
        try {
            String transcriptInput = trimForAi(fullText);
            BeanOutputConverter<TranscriptAiSummary> converter = new BeanOutputConverter<>(TranscriptAiSummary.class);
            TranscriptAiSummary aiSummary = chatClient.prompt()
                    .system("""
                            You summarize finalized body-cam transcripts for operators.
                            Keep summaries factual, concise, and grounded only in the provided transcript.
                            Do not invent details, names, or outcomes.
                            Return structured JSON only.
                            """)
                    .user(user -> user.text("""
                            Create an operator-facing transcript summary.

                            Recording count: {recordingCount}
                            Transcript segment count: {segmentCount}

                            Requirements:
                            - shortSummary: 1 to 2 sentences, under 320 characters.
                            - incidentSummary: 1 compact paragraph focused on what happened operationally.
                            - keywords: up to 5 lowercase keywords, no duplicates.

                            Transcript:
                            {transcript}

                            {format}
                            """)
                            .param("recordingCount", recordingCount)
                            .param("segmentCount", segmentCount)
                            .param("transcript", transcriptInput)
                            .param("format", converter.getFormat()))
                    .call()
                    .entity(converter);

            SessionTranscriptSummary sanitized = sanitizeAiSummary(aiSummary, fullText, recordingCount, segmentCount);
            if (sanitized == null) {
                log.warn("Transcript AI summary returned empty structured payload; falling back to heuristic summary");
            }
            return sanitized;
        } catch (Exception exception) {
            log.warn("Transcript AI summarization failed; falling back to heuristic summary", exception);
            return null;
        }
    }

    private SessionTranscriptSummary sanitizeAiSummary(
            TranscriptAiSummary aiSummary,
            String fallbackText,
            int recordingCount,
            int segmentCount) {
        if (aiSummary == null) {
            return null;
        }

        String shortSummary = trimToNull(aiSummary.shortSummary());
        String incidentSummary = trimToNull(aiSummary.incidentSummary());
        List<String> keywords = sanitizeKeywords(aiSummary.keywords());

        if (shortSummary == null && incidentSummary == null && keywords.isEmpty()) {
            return null;
        }

        if (shortSummary != null && shortSummary.length() > 320) {
            shortSummary = shortSummary.substring(0, 317).trim() + "...";
        }
        if (incidentSummary == null) {
            incidentSummary = buildIncidentSummary(recordingCount, segmentCount, keywords, shortSummary);
        }
        if (keywords.isEmpty()) {
            keywords = keywords(fallbackText);
        }

        return new SessionTranscriptSummary(shortSummary, incidentSummary, keywords);
    }

    private List<String> sanitizeKeywords(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        for (String value : values) {
            String normalized = normalizedForComparison(value);
            if (normalized.isBlank() || normalized.length() < 4 || STOP_WORDS.contains(normalized)) {
                continue;
            }
            unique.putIfAbsent(normalized, Boolean.TRUE);
            if (unique.size() >= 5) {
                break;
            }
        }
        return List.copyOf(unique.keySet());
    }

    private String trimForAi(String fullText) {
        int maxLength = Math.max(1000, appProperties.transcript().summary().aiMaxInputChars());
        if (fullText.length() <= maxLength) {
            return fullText;
        }
        return fullText.substring(0, maxLength).trim();
    }

    private String cacheKey(String fullText, int recordingCount, int segmentCount) {
        String payload = recordingCount + "|" + segmentCount + "|" + fullText;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizedForComparison(String value) {
        if (value == null) {
            return "";
        }
        return WORD_SPLIT.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
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

    private record TranscriptAiSummary(
            String shortSummary,
            String incidentSummary,
            List<String> keywords
    ) {
    }
}
