package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record AlertEvent(
        Long id,
        Long alertRuleId,
        long channelId,
        String alertType,
        String message,
        String severity,
        boolean acknowledged,
        Instant createdAt
) implements Serializable {}
