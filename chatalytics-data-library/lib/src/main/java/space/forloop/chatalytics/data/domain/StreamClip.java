package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record StreamClip(
        String id,
        String url,
        String embedUrl,
        String title,
        int viewCount,
        String createdAt,
        String thumbnailUrl,
        double duration,
        String creatorName
) implements Serializable {}
