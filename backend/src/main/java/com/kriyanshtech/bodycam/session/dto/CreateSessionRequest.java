package com.kriyanshtech.bodycam.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSessionRequest(
        UUID workerId,
        @NotBlank @Size(max = 160) String workerName,
        @NotBlank @Size(max = 120) String referenceNumber
) {
}
