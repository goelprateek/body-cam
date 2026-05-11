package com.kriyanshtech.bodycam.auth.service;

import com.kriyanshtech.bodycam.auth.entity.AppUser;
import com.kriyanshtech.bodycam.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class JwtService {
    private static final int MIN_SECRET_BYTES = 32;
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final AppProperties appProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(AppProperties appProperties) {
        this.appProperties = appProperties;
        byte[] secretBytes = appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 characters (256 bits) for HS256 signing");
        }

        SecretKey key = new SecretKeySpec(secretBytes, "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public String generateAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(appProperties.jwt().accessTokenMinutes(), ChronoUnit.MINUTES);
        log.info(
                "Generating access token userId={} username={} role={} issuer={} expiresAt={}",
                user.getId(),
                user.getUsername(),
                user.getRole(),
                appProperties.jwt().issuer(),
                expiry
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(appProperties.jwt().issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiry)
                .claim("username", user.getUsername())
                .claim("displayName", user.getDisplayName())
                .claim("role", user.getRole())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        log.info(
                "Generated access token userId={} username={} tokenLength={}",
                user.getId(),
                user.getUsername(),
                tokenValue.length()
        );
        return tokenValue;
    }

    public AuthenticatedUser parse(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                jwt.getClaimAsString("displayName"),
                jwt.getClaimAsString("role")
        );
        log.info(
                "Parsed access token userId={} username={} role={} issuedAt={} expiresAt={}",
                user.userId(),
                user.username(),
                user.role(),
                jwt.getIssuedAt(),
                jwt.getExpiresAt()
        );

        return user;
    }

    public record AuthenticatedUser(
            UUID userId,
            String username,
            String displayName,
            String role
    ) {
    }
}
