package com.kriyanshtech.bodycam.recording.transcript;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptProcessingStage;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TranscriptRecoveryService {

    public TranscriptRecoveryDecision evaluate(
            RecordingTranscript transcript,
            RecordingTranscriptProcessingStage failureStage,
            Exception exception,
            int maxRetryCount) {
        int currentRetryCount = transcript.getRetryCount() == null ? 0 : transcript.getRetryCount();
        int nextRetryCount = currentRetryCount + 1;
        String errorMessage = RecordingTranscriptSupport.trimMessage(exception.getMessage());
        boolean recoverable = isRecoverable(failureStage, exception);
        boolean requeue = recoverable && nextRetryCount <= maxRetryCount;
        return new TranscriptRecoveryDecision(requeue, nextRetryCount, failureStage, errorMessage);
    }

    private boolean isRecoverable(RecordingTranscriptProcessingStage failureStage, Exception exception) {
        if (failureStage == RecordingTranscriptProcessingStage.FINALIZED
                || failureStage == RecordingTranscriptProcessingStage.PUNCTUATED) {
            return false;
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("timed out")
                || normalized.contains("timeout")
                || normalized.contains("connection refused")
                || normalized.contains("connect")
                || normalized.contains("i/o error")
                || normalized.contains("http 502")
                || normalized.contains("http 503")
                || normalized.contains("http 504")
                || normalized.contains("temporarily unavailable");
    }
}
