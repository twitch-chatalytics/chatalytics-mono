package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record Viewer(
        long twitchId,
        String login,
        String displayName,
        String profileImageUrl,
        Instant createdAt
) implements Serializable {}
