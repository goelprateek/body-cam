package com.kriyanshtech.bodycam.common;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class CreatedAtUuidCursorCodec {
    private static final String CURSOR_PREFIX = "v1|";

    public String encode(CreatedAtUuidCursor cursor) {
        String payload = cursor.createdAt().toString() + "|" + cursor.id();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((CURSOR_PREFIX + payload).getBytes(StandardCharsets.UTF_8));
    }

    public CreatedAtUuidCursor decode(String cursorValue) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursorValue), StandardCharsets.UTF_8);
            if (!decoded.startsWith(CURSOR_PREFIX)) {
                throw new IllegalArgumentException("Invalid cursor format");
            }
            String[] parts = decoded.substring(CURSOR_PREFIX.length()).split("\\|");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid cursor payload");
            }
            return new CreatedAtUuidCursor(
                    Instant.parse(parts[0]),
                    UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid cursor format", exception);
        }
    }
}
