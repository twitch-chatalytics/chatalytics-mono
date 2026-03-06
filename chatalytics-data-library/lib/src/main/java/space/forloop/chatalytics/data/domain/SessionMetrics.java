package space.forloop.chatalytics.data.domain;

public record SessionMetrics(
        long messagesPerMinute,
        long totalChatters,
        long totalMessages,
        long totalSessions,
        long totalMentions,

        String topChatterByMessageCount,
        long topChatterByMessageCountValue) {
}
