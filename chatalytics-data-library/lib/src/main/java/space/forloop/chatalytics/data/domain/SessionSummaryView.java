package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record SessionSummaryView(
        long sessionId,
        long twitchId,
        Instant startTime,
        Instant endTime,
        long totalMessages,
        long totalChatters,
        String lastGameName
) implements Serializable {}
