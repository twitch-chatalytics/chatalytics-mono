package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record HypeMoment(
        Instant timestamp,
        long messageCount,
        long uniqueChatters,
        double multiplier
) implements Serializable {}
