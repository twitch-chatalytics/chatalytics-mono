package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record StreamRecap(
        long sessionId,
        Instant startTime,
        Instant endTime,
        long totalMessages,
        long totalChatters,
        List<StreamSnapshot> snapshots,
        List<ChatActivityBucket> chatActivity,
        List<TopChatter> topChatters,
        String aiSummary
) implements Serializable {}
