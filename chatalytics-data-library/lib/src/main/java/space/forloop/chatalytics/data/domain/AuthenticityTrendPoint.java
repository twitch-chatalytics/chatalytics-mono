package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record AuthenticityTrendPoint(
        Instant date,
        int score,
        Integer viewerCount
) implements Serializable {}
