package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record AlertRule(
        Long id,
        long channelId,
        String alertType,
        Double thresholdValue,
        boolean enabled,
        Instant createdAt
) implements Serializable {}
