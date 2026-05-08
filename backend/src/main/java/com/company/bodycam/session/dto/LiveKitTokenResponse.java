package com.company.bodycam.session.dto;

public record LiveKitTokenResponse(
        String token,
        String roomName,
        String liveKitUrl
) {
}
