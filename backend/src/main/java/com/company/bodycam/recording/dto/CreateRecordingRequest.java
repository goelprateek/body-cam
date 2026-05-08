package com.company.bodycam.recording.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRecordingRequest(
        @NotNull UUID sessionId,
        @NotBlank String objectKey,
        @NotBlank String playbackUrl,
        Integer durationSeconds
) {
}
