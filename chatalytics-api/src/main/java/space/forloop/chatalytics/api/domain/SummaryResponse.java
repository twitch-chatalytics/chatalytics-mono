package space.forloop.chatalytics.api.domain;

import lombok.Builder;

@Builder
public record SummaryResponse(
        double messagesPerMinute,
        double totalChatters,
        double totalMessages,
        double totalSessions,
        double totalMentions
) {
}
