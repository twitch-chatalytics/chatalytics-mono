package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record ChannelProfile(
        long id,
        String login,
        String displayName,
        String broadcasterType,
        String description,
        String profileImageUrl,
        String offlineImageUrl,
        Instant createdAt
) implements Serializable {}
