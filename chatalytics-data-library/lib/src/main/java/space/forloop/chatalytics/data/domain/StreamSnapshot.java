package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record StreamSnapshot(
        long id,
        long sessionId,
        long twitchId,
        Instant timestamp,
        String gameName,
        String title,
        int viewerCount
) implements Serializable {}
