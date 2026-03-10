package space.forloop.chatalytics.data.domain;

import java.time.Instant;

public record ChatMessage(
    String platform,
    String channel,
    String authorId,
    String authorName,
    String messageText,
    Instant timestamp,
    SessionWithUser session
) {}
