package com.kriyanshtech.bodycam.session.dto;

import jakarta.validation.constraints.Pattern;

public record CreateSessionInviteRequest(
        @Pattern(regexp = "WORKER|OPERATOR|BROWSER|VIEWER") String participantRole
) {
}
