package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record GameSegment(
        String gameName,
        Instant startTime,
        Instant endTime,
        long durationMinutes,
        long messageCount,
        double avgViewers,
        int peakViewers
) implements Serializable {}
