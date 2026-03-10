package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record Viewer(
        long channelId,
        String login,
        String displayName,
        String profileImageUrl,
        Instant createdAt
) implements Serializable {}
