package com.kriyanshtech.bodycam.common;

import java.time.Instant;
import java.util.UUID;

public record CreatedAtUuidCursor(
        Instant createdAt,
        UUID id
) {
}
