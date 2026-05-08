package com.kriyanshtech.bodycam.auth.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String username,
        String displayName,
        String role
) {
}
