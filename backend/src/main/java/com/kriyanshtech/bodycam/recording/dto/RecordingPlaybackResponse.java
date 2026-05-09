package com.kriyanshtech.bodycam.recording.dto;

import java.util.UUID;

public record RecordingPlaybackResponse(
        UUID recordingId,
        String playbackUrl,
        int expiresInSeconds
) {
}
