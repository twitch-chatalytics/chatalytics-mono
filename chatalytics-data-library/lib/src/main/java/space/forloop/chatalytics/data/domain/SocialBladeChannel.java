package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;

public record SocialBladeChannel(
        long twitchId,
        String username,
        String displayName,
        Long followers,
        Long views,
        String grade,
        Integer rank,
        Integer followerRank,

        // Gains
        Integer followersGained30d,
        Integer followersGained90d,
        Integer followersGained180d,
        Long viewsGained30d,
        Long viewsGained90d,
        Long viewsGained180d,

        // Social links
        String youtubeUrl,
        String twitterUrl,
        String instagramUrl,
        String discordUrl,
        String tiktokUrl,

        Instant fetchedAt,
        Instant updatedAt
) implements Serializable {}
