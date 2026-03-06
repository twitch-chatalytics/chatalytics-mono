package space.forloop.chatalytics.data.domain;

import lombok.Builder;

import java.time.Instant;

@Builder
public record SessionWithUser(Long id, Long twitchId, Instant startTime, Instant endTime, String login) {
}
