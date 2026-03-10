package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record SessionSummaryView(
        long sessionId,
        long channelId,
        Instant startTime,
        Instant endTime,
        long totalMessages,
        long totalChatters,
        String lastGameName,
        Integer peakViewerCount,
        Double messagesPerMinute,
        Long durationMinutes
) implements Serializable {}
