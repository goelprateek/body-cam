package com.kriyanshtech.bodycam.recording.transcript;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TranscriptFinalizationService {
    private static final Pattern WORD_SPLIT = Pattern.compile("\\s+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9']+");

    public ProcessedTranscriptResult finalizeTranscript(
            String engine,
            List<TranscriptSegmentPayload> punctuatedSegments) {
        assertQuality(engine, punctuatedSegments);

        List<TranscriptSegmentPayload> finalized = new ArrayList<>();
        String previousText = null;

        for (TranscriptSegmentPayload segment : punctuatedSegments) {
            if (!isTimelineSafe(segment)) {
                continue;
            }

            String normalized = collapseRepeatedWords(segment.text());
            normalized = removeOverlap(previousText, normalized);
            normalized = capitalizeSentence(normalized);
            normalized = ensureSentence(normalized);
            if (normalized.isBlank()) {
                continue;
            }

            TranscriptSegmentPayload candidate = new TranscriptSegmentPayload(
                    segment.startSeconds(),
                    segment.endSeconds(),
                    normalized,
                    segment.confidence());

            finalized.add(candidate);
            previousText = candidate.text();
        }

        assertQuality(engine, finalized);

        String fullText = finalized.stream()
                .map(TranscriptSegmentPayload::text)
                .reduce("", (left, right) -> left.isBlank() ? right : left + " " + right)
                .trim();

        return new ProcessedTranscriptResult(fullText.isBlank() ? null : fullText, finalized);
    }

    private boolean isTimelineSafe(TranscriptSegmentPayload segment) {
        if (segment == null || segment.startSeconds() == null || segment.endSeconds() == null) {
            return false;
        }
        return segment.endSeconds().compareTo(segment.startSeconds()) > 0;
    }

    private String collapseRepeatedWords(String text) {
        List<String> words = words(text);
        if (words.isEmpty()) {
            return "";
        }

        List<String> collapsed = new ArrayList<>();
        String previous = null;
        for (String word : words) {
            if (previous != null && previous.equalsIgnoreCase(word)) {
                continue;
            }
            collapsed.add(word);
            previous = word;
        }
        return String.join(" ", collapsed).trim();
    }

    private String removeOverlap(String previousText, String currentText) {
        if (previousText == null || previousText.isBlank() || currentText.isBlank()) {
            return currentText;
        }

        List<String> previousWords = words(previousText);
        List<String> currentWords = words(currentText);
        if (previousWords.isEmpty() || currentWords.isEmpty()) {
            return currentText;
        }

        int maxOverlap = Math.min(4, Math.min(previousWords.size(), currentWords.size()));
        for (int overlap = maxOverlap; overlap >= 1; overlap--) {
            boolean matches = true;
            for (int index = 0; index < overlap; index++) {
                String previousWord = previousWords.get(previousWords.size() - overlap + index);
                String currentWord = currentWords.get(index);
                if (!previousWord.equalsIgnoreCase(currentWord)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                List<String> trimmed = currentWords.subList(overlap, currentWords.size());
                return String.join(" ", trimmed).trim();
            }
        }

        return currentText;
    }

    private void assertQuality(String engine, List<TranscriptSegmentPayload> segments) {
        if (segments.isEmpty()) {
            return;
        }

        List<String> allWords = new ArrayList<>();
        Set<String> uniqueWords = new HashSet<>();
        for (TranscriptSegmentPayload segment : segments) {
            for (String word : words(segment.text())) {
                String normalized = normalizedForComparison(word);
                if (normalized.isBlank()) {
                    continue;
                }
                allWords.add(normalized);
                uniqueWords.add(normalized);
            }
        }

        if (allWords.isEmpty()) {
            return;
        }

        BigDecimal maxEnd = segments.stream()
                .map(TranscriptSegmentPayload::endSeconds)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        boolean repeatedSingleWordAcrossSegments = segments.size() >= 3
                && maxEnd.compareTo(BigDecimal.valueOf(15L)) >= 0
                && segments.stream()
                        .map(segment -> words(segment.text()))
                        .allMatch(words -> words.size() == 1)
                && uniqueWords.size() == 1;

        if (repeatedSingleWordAcrossSegments) {
            String repeatedWord = uniqueWords.iterator().next();
            throw new IllegalStateException(
                    "Transcript finalization rejected low-quality " + engine
                            + " output: repeated single-word transcript \"" + repeatedWord + "\"");
        }

        double lexicalDiversity = uniqueWords.size() / (double) allWords.size();
        if (maxEnd.compareTo(BigDecimal.valueOf(30L)) >= 0
                && allWords.size() >= 6
                && lexicalDiversity < 0.2d) {
            throw new IllegalStateException(
                    "Transcript finalization rejected low-diversity " + engine
                            + " output for a long recording; likely poor raw STT quality");
        }
    }

    private String ensureSentence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        char lastChar = trimmed.charAt(trimmed.length() - 1);
        if (lastChar == '.' || lastChar == '!' || lastChar == '?') {
            return trimmed;
        }
        return trimmed + ".";
    }

    private String capitalizeSentence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        char first = trimmed.charAt(0);
        return Character.toUpperCase(first) + trimmed.substring(1);
    }

    private List<String> words(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return List.of();
        }
        String[] parts = WORD_SPLIT.split(trimmed.replaceAll("[.!?]+$", ""));
        List<String> words = new ArrayList<>(parts.length);
        for (String part : parts) {
            String normalized = part.trim();
            if (!normalized.isBlank()) {
                words.add(normalized);
            }
        }
        return words;
    }

    private String normalizedForComparison(String value) {
        if (value == null) {
            return "";
        }
        return NON_WORD.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");
    }
}
