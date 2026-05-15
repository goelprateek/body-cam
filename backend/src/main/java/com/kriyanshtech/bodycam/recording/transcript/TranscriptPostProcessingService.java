package com.kriyanshtech.bodycam.recording.transcript;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranscriptPostProcessingService {
    private final PunctuationRestorationService punctuationRestorationService;
    private final TranscriptFinalizationService transcriptFinalizationService;

    public TranscriptPostProcessingService(
            PunctuationRestorationService punctuationRestorationService,
            TranscriptFinalizationService transcriptFinalizationService) {
        this.punctuationRestorationService = punctuationRestorationService;
        this.transcriptFinalizationService = transcriptFinalizationService;
    }

    public List<TranscriptSegmentPayload> punctuate(RecordingTranscriptGenerationResult result) {
        return punctuationRestorationService.restore(result.segments());
    }

    public ProcessedTranscriptResult finalizeTranscript(
            RecordingTranscriptGenerationResult result,
            List<TranscriptSegmentPayload> punctuatedSegments) {
        return transcriptFinalizationService.finalizeTranscript(result.engine(), punctuatedSegments);
    }

    public List<com.kriyanshtech.bodycam.recording.dto.SessionTranscriptSegmentResponse> finalizeSessionSegments(
            List<com.kriyanshtech.bodycam.recording.dto.SessionTranscriptSegmentResponse> sessionSegments) {
        return transcriptFinalizationService.finalizeSessionSegments(sessionSegments);
    }
}
