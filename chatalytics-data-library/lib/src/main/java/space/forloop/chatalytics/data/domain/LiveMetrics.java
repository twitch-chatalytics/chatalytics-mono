package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record LiveMetrics(
        long twitchId,
        long sessionId,
        Instant timestamp,
        double messagesPerMinute,
        int activeChatters,
        int viewerCount,
        boolean isHype,
        double hypeMultiplier,
        List<TopWord> topWords,
        long totalMessages,
        int totalChatters
) implements Serializable {}
