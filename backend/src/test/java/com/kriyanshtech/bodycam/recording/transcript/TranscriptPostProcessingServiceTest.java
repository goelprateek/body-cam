package com.kriyanshtech.bodycam.recording.transcript;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TranscriptPostProcessingServiceTest {

    private final TranscriptPostProcessingService service = new TranscriptPostProcessingService(
            new PunctuationRestorationService(),
            new TranscriptFinalizationService());

    @Test
    void punctuateAndFinalizeRemovesOverlapAndBuildsReadableText() {
        RecordingTranscriptGenerationResult result = new RecordingTranscriptGenerationResult(
                "vosk",
                "model",
                "en-ZA",
                null,
                List.of(
                        segment("hello there", 0.0, 2.0),
                        segment("there officer", 2.05, 4.0),
                        segment("copy copy", 4.10, 5.0)));

        List<TranscriptSegmentPayload> punctuated = service.punctuate(result);
        ProcessedTranscriptResult processed = service.finalizeTranscript(result, punctuated);

        assertEquals("Hello there. Officer. Copy.", processed.fullText());
        assertEquals(3, processed.segments().size());
        assertEquals("Hello there.", processed.segments().get(0).text());
        assertEquals("Officer.", processed.segments().get(1).text());
        assertEquals("Copy.", processed.segments().get(2).text());
    }

    @Test
    void finalizationRejectsDegenerateRepeatedSingleWordTranscript() {
        RecordingTranscriptGenerationResult result = new RecordingTranscriptGenerationResult(
                "vosk",
                "model",
                "en-ZA",
                null,
                List.of(
                        segment("the", 0.27, 19.57),
                        segment("the", 20.19, 40.32),
                        segment("the", 40.35, 51.66)));

        List<TranscriptSegmentPayload> punctuated = service.punctuate(result);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.finalizeTranscript(result, punctuated));

        assertEquals(
                "Transcript finalization rejected low-quality vosk output: repeated single-word transcript \"the\"",
                exception.getMessage());
    }

    @Test
    void finalizationKeepsDistinctShortSegmentsTimelineSafe() {
        RecordingTranscriptGenerationResult result = new RecordingTranscriptGenerationResult(
                "faster-whisper",
                "model",
                "en-ZA",
                null,
                List.of(
                        segment("unit arrived", 0.0, 1.8),
                        segment("scene secure", 2.0, 3.5),
                        segment("request backup", 3.8, 5.2)));

        List<TranscriptSegmentPayload> punctuated = service.punctuate(result);
        ProcessedTranscriptResult processed = service.finalizeTranscript(result, punctuated);

        assertEquals("Unit arrived. Scene secure. Request backup.", processed.fullText());
        assertEquals(3, processed.segments().size());
        assertEquals(BigDecimal.valueOf(0.0).setScale(2), processed.segments().get(0).startSeconds());
        assertEquals(BigDecimal.valueOf(5.2).setScale(2), processed.segments().get(2).endSeconds());
    }

    private TranscriptSegmentPayload segment(String text, double start, double end) {
        return new TranscriptSegmentPayload(
                RecordingTranscriptSupport.decimal(start),
                RecordingTranscriptSupport.decimal(end),
                text,
                BigDecimal.valueOf(0.9d).setScale(4, java.math.RoundingMode.HALF_UP));
    }
}
