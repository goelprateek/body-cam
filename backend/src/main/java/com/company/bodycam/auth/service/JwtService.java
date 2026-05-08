package com.company.bodycam.auth.service;

import com.company.bodycam.auth.entity.AppUser;
import com.company.bodycam.config.AppProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
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

    private final AppProperties appProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(AppProperties appProperties) {
        this.appProperties = appProperties;
        SecretKey key = new SecretKeySpec(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public String generateAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(appProperties.jwt().accessTokenMinutes(), ChronoUnit.MINUTES);

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
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public AuthenticatedUser parse(String token) {
        Jwt jwt = jwtDecoder.decode(token);

        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("username"),
                jwt.getClaimAsString("displayName"),
                jwt.getClaimAsString("role")
        );
    }

    public record AuthenticatedUser(
            UUID userId,
            String username,
            String displayName,
            String role
    ) {
    }
}
