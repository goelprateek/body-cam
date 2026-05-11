package com.kriyanshtech.bodycam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        LiveKit livekit,
        Storage storage,
        Transcript transcript
) {

    public record Jwt(
            String issuer,
            String secret,
            long accessTokenMinutes
    ) {
    }

    public record LiveKit(
            String url,
            String publicUrl,
            String apiKey,
            String apiSecret
    ) {
    }

    public record Storage(
            String endpoint,
            String publicUrl,
            String region,
            String bucket,
            String accessKey,
            String secretKey
    ) {
    }

    public record Transcript(
            boolean enabled,
            String engine,
            String voskUrl,
            String languageCode,
            long pollDelayMs,
            FasterWhisper fasterWhisper
    ) {
    }

    public record FasterWhisper(
            String url,
            String model,
            String task,
            long timeoutSeconds
    ) {
    }
}
