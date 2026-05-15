package com.kriyanshtech.bodycam.recording.entity;

public enum RecordingTranscriptProcessingStage {
    QUEUED,
    TRANSCRIBING,
    TRANSCRIBED,
    PUNCTUATED,
    FINALIZED,
    FAILED
}
