package com.kriyanshtech.bodycam.recording.service;

import com.kriyanshtech.bodycam.recording.dto.RecordingInvestigationSearchHitResponse;
import com.kriyanshtech.bodycam.recording.dto.RecordingInvestigationSearchResponse;
import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptSegment;
import com.kriyanshtech.bodycam.recording.repository.RecordingAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RecordingInvestigationService {
    private static final Logger log = LoggerFactory.getLogger(RecordingInvestigationService.class);
    private static final int MAX_HITS = 50;
    private final RecordingAssetRepository recordingAssetRepository;

    public RecordingInvestigationService(RecordingAssetRepository recordingAssetRepository) {
        this.recordingAssetRepository = recordingAssetRepository;
    }

    @Transactional(readOnly = true)
    public RecordingInvestigationSearchResponse search(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            throw new IllegalArgumentException("Investigation search query is required");
        }

        String loweredQuery = normalizedQuery.toLowerCase(Locale.ROOT);
        List<RecordingInvestigationSearchHitResponse> hits = new ArrayList<>();
        for (RecordingAsset recording : recordingAssetRepository.findAllByOrderByCreatedAtDesc()) {
            if (hits.size() >= MAX_HITS) {
                break;
            }
            collectRecordingHits(recording, normalizedQuery, loweredQuery, hits);
        }

        log.info("Recording investigation search completed queryLength={} totalMatches={}", normalizedQuery.length(), hits.size());
        return new RecordingInvestigationSearchResponse(normalizedQuery, hits.size(), hits);
    }

    private void collectRecordingHits(
            RecordingAsset recording,
            String normalizedQuery,
            String loweredQuery,
            List<RecordingInvestigationSearchHitResponse> hits) {
        addFieldHitIfMatched(recording, "referenceNumber", recording.getSession().getReferenceNumber(), loweredQuery, hits, null);
        addFieldHitIfMatched(recording, "workerName", recording.getSession().getWorkerName(), loweredQuery, hits, null);
        addFieldHitIfMatched(recording, "roomName", recording.getSession().getRoomName(), loweredQuery, hits, null);

        if (recording.getTranscript() == null || recording.getTranscript().getSegments() == null) {
            return;
        }
        for (RecordingTranscriptSegment segment : recording.getTranscript().getSegments()) {
            if (hits.size() >= MAX_HITS) {
                return;
            }
            String text = segment.getText();
            if (text == null || !text.toLowerCase(Locale.ROOT).contains(loweredQuery)) {
                continue;
            }
            hits.add(new RecordingInvestigationSearchHitResponse(
                    recording.getSession().getId(),
                    recording.getId(),
                    RecordingTimelineSupport.segmentSequence(recording),
                    recording.getSession().getWorkerId(),
                    recording.getSession().getWorkerName(),
                    recording.getSession().getRoomName(),
                    recording.getSession().getReferenceNumber(),
                    "transcript",
                    buildSnippet(text, normalizedQuery),
                    segment.getStartSeconds(),
                    recording.getCreatedAt()
            ));
            return;
        }
    }

    private void addFieldHitIfMatched(
            RecordingAsset recording,
            String matchedField,
            String value,
            String loweredQuery,
            List<RecordingInvestigationSearchHitResponse> hits,
            BigDecimal transcriptStartSeconds) {
        if (value == null || hits.size() >= MAX_HITS || !value.toLowerCase(Locale.ROOT).contains(loweredQuery)) {
            return;
        }
        hits.add(new RecordingInvestigationSearchHitResponse(
                recording.getSession().getId(),
                recording.getId(),
                RecordingTimelineSupport.segmentSequence(recording),
                recording.getSession().getWorkerId(),
                recording.getSession().getWorkerName(),
                recording.getSession().getRoomName(),
                recording.getSession().getReferenceNumber(),
                matchedField,
                value,
                transcriptStartSeconds,
                recording.getCreatedAt()
        ));
    }

    private String buildSnippet(String text, String query) {
        String loweredText = text.toLowerCase(Locale.ROOT);
        String loweredQuery = query.toLowerCase(Locale.ROOT);
        int matchIndex = loweredText.indexOf(loweredQuery);
        if (matchIndex < 0) {
            return text;
        }
        int snippetStart = Math.max(0, matchIndex - 36);
        int snippetEnd = Math.min(text.length(), matchIndex + query.length() + 48);
        String snippet = text.substring(snippetStart, snippetEnd).trim();
        if (snippetStart > 0) {
            snippet = "..." + snippet;
        }
        if (snippetEnd < text.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }
}
