package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChannelBrandSafety(
        long channelId,
        int brandSafetyScore,
        Double toxicityRate,
        Double positiveRate,
        Double negativeRate,
        Double neutralRate,
        Double emoteSpamRate,
        Double conversationRatio,
        List<TopicCount> topTopics,
        Map<String, Double> languageDistribution,
        int sessionsAnalyzed,
        Instant updatedAt
) implements Serializable {
    public record TopicCount(String topic, int count) implements Serializable {}
}
