package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record ChatActivityBucket(
        Instant bucketStart,
        long messageCount,
        long uniqueChatters
) implements Serializable {}
