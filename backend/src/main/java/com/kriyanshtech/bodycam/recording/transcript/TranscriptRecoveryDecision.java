package com.kriyanshtech.bodycam.recording.transcript;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptProcessingStage;

public record TranscriptRecoveryDecision(
        boolean requeue,
        int nextRetryCount,
        RecordingTranscriptProcessingStage failureStage,
        String errorMessage
) {
}
