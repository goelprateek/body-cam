package com.kriyanshtech.bodycam.session.service;

import com.kriyanshtech.bodycam.config.AppProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveKitTokenService {

    private final AppProperties appProperties;

    public LiveKitTokenService(AppProperties appProperties) {
        this.appProperties = appProperties;
        validateLiveKitSecret();
    }

    public String createJoinToken(String participantName, String roomName, String participantRole) {
        Instant now = Instant.now();
        Instant expiry = now.plus(8, ChronoUnit.HOURS);
        boolean canPublish = "WORKER".equals(participantRole);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(appProperties.livekit().apiKey())
                .subject(UUID.randomUUID().toString())
                .issueTime(java.util.Date.from(now))
                .expirationTime(java.util.Date.from(expiry))
                .claim("name", participantName)
                .claim("video", Map.of(
                        "room", roomName,
                        "roomJoin", true,
                        "canPublish", canPublish,
                        "canSubscribe", true,
                        "canPublishData", true
                ))
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);

        try {
            signedJwt.sign(new MACSigner(appProperties.livekit().apiSecret().getBytes(StandardCharsets.UTF_8)));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to sign LiveKit token", exception);
        }
    }

    private void validateLiveKitSecret() {
        String apiSecret = appProperties.livekit().apiSecret();
        if (apiSecret == null || apiSecret.length() < 32) {
            throw new IllegalStateException(
                    "LIVEKIT_API_SECRET must be at least 32 characters for HS256 token signing"
            );
        }
    }
}
