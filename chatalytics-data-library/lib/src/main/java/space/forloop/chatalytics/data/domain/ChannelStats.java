package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.util.List;

public record ChannelStats(
        long totalMessages,
        long uniqueChatters,
        List<TopChatter> topChatters,
        Integer peakHour,
        long totalSessions,
        double avgMessagesPerSession,
        double avgChattersPerSession,
        Double avgStreamDurationMinutes,
        List<TopGame> topGames
) implements Serializable {}
