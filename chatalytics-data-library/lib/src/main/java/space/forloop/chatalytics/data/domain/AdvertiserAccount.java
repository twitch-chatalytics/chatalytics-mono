package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record AdvertiserAccount(
        long id,
        long viewerId,
        String tier,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant updatedAt
) implements Serializable {}
