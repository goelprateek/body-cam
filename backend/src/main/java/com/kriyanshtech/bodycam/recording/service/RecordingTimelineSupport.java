package com.kriyanshtech.bodycam.recording.service;

import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;
import com.kriyanshtech.bodycam.recording.entity.RecordingMetadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;

final class RecordingTimelineSupport {
    private RecordingTimelineSupport() {
    }

    static Comparator<RecordingAsset> segmentTimelineComparator() {
        Comparator<Integer> nullableSequenceComparator = Comparator.nullsLast(Integer::compareTo);
        Comparator<Instant> nullableInstantComparator = Comparator.nullsLast(Instant::compareTo);
        Comparator<Long> nullableLongComparator = Comparator.nullsLast(Long::compareTo);

        return Comparator
                .comparing(RecordingTimelineSupport::segmentSequence, nullableSequenceComparator)
                .thenComparing(RecordingTimelineSupport::sessionElapsedStartMs, nullableLongComparator)
                .thenComparing(RecordingTimelineSupport::segmentStartedAt, nullableInstantComparator)
                .thenComparing(RecordingTimelineSupport::capturedAt, nullableInstantComparator)
                .thenComparing(RecordingAsset::getCreatedAt);
    }

    static Integer segmentSequence(RecordingAsset asset) {
        return metadata(asset).getSegmentSequence();
    }

    static Long sessionElapsedStartMs(RecordingAsset asset) {
        return metadata(asset).getSessionElapsedStartMs();
    }

    static Long sessionElapsedEndMs(RecordingAsset asset) {
        return metadata(asset).getSessionElapsedEndMs();
    }

    static Instant segmentStartedAt(RecordingAsset asset) {
        return metadata(asset).getSegmentStartedAt();
    }

    static Instant capturedAt(RecordingAsset asset) {
        return metadata(asset).getCapturedAt();
    }

    static long inferredSessionStartMs(RecordingAsset asset, long fallbackMs) {
        Long explicitStart = sessionElapsedStartMs(asset);
        return explicitStart != null && explicitStart >= 0 ? explicitStart : fallbackMs;
    }

    static long inferredSessionEndMs(RecordingAsset asset, long fallbackStartMs) {
        Long explicitEnd = sessionElapsedEndMs(asset);
        if (explicitEnd != null && explicitEnd >= 0) {
            return explicitEnd;
        }

        Integer durationSeconds = asset.getDurationSeconds();
        if (durationSeconds != null && durationSeconds > 0) {
            return fallbackStartMs + (durationSeconds * 1000L);
        }

        return fallbackStartMs;
    }

    static BigDecimal millisToSeconds(long millis) {
        return BigDecimal.valueOf(millis).movePointLeft(3);
    }

    private static RecordingMetadata metadata(RecordingAsset asset) {
        return asset.getMetadata() == null ? EmptyRecordingMetadata.INSTANCE : asset.getMetadata();
    }

    private static final class EmptyRecordingMetadata extends RecordingMetadata {
        private static final EmptyRecordingMetadata INSTANCE = new EmptyRecordingMetadata();
    }
}
