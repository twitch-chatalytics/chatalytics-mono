package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record ChannelBenchmark(
        long channelId,
        int percentileRank,
        String viewerTier,
        double tierAvgScore,
        double globalAvgScore,
        int channelsInTier,
        String primaryCategory,
        Double categoryAvgScore,
        int channelsInCategory
) implements Serializable {}
