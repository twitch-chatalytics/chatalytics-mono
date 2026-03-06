package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record ChatterProfile(
        String author,
        long totalMessages,
        Instant firstSeen,
        Instant lastSeen,
        long distinctSessions,
        Integer peakHour,
        double avgMessagesPerSession,
        List<RepeatedMessage> repeatedMessages
) implements Serializable {}
