package com.kriyanshtech.bodycam.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID workerId,
        @NotBlank String workerName
) {
}
