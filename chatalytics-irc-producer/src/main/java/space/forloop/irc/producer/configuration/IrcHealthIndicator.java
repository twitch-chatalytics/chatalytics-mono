package space.forloop.irc.producer.configuration;

import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import space.forloop.irc.producer.services.ChannelService;

@Component
@RequiredArgsConstructor
public class IrcHealthIndicator implements HealthIndicator {

    private final PircBotX pircBotX;
    private final ChannelService channelService;

    @Override
    public Health health() {
        if (pircBotX.isConnected()) {
            return Health.up()
                    .withDetail("joinedChannels", channelService.getJoinedChannels().size())
                    .build();
        }
        return Health.down()
                .withDetail("reason", "IRC bot is not connected")
                .build();
    }
}
