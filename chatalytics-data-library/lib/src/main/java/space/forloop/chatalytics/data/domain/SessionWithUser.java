package space.forloop.chatalytics.data.domain;

import lombok.Builder;

import java.time.Instant;

@Builder
public record SessionWithUser(Long id, Long channelId, String platform, Instant startTime, Instant endTime, String login) {
}
