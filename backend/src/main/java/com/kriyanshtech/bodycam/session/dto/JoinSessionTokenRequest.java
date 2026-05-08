package com.kriyanshtech.bodycam.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record JoinSessionTokenRequest(
        @NotBlank String participantName,
        @Pattern(regexp = "WORKER|OPERATOR") String participantRole
) {
}
