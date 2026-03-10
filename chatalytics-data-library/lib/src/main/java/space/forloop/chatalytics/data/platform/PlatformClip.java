package space.forloop.chatalytics.data.platform;

import java.time.Instant;

public record PlatformClip(
    String id,
    String url,
    String embedUrl,
    String title,
    int viewCount,
    Instant createdAt,
    String thumbnailUrl,
    double duration,
    String creatorName
) {}
