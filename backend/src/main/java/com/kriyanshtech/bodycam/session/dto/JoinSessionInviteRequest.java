package com.kriyanshtech.bodycam.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinSessionInviteRequest(
        @NotBlank @Size(max = 160) String participantName
) {
}
