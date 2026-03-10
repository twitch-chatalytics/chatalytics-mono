package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ChannelAuthenticity(
        long channelId,
        Double avgAuthenticityScore,
        Integer minAuthenticityScore,
        Integer maxAuthenticityScore,
        String trendDirection,
        int sessionsAnalyzed,
        String riskLevel,
        List<String> riskFactors,
        Instant updatedAt
) implements Serializable {}
