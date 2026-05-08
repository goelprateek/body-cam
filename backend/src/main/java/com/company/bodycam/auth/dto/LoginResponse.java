package com.company.bodycam.auth.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        UUID userId,
        String username,
        String displayName,
        String role
) {
}
