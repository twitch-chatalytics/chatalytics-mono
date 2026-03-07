package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record StreamerRequestSummary(
        String streamerLogin,
        Long streamerId,
        String displayName,
        String profileImageUrl,
        long voteCount
) implements Serializable {}
