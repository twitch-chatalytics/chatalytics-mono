package space.forloop.irc.producer.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.data.domain.IrcPayload;
import space.forloop.irc.producer.services.KafkaProducerService;
import space.forloop.irc.producer.services.SessionMapService;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class OnIrcMessageEvent extends ListenerAdapter {

    public final SessionMapService sessionMapService;

    private final KafkaProducerService kafkaProducerService;

    @Override
    public void onMessage(final MessageEvent event) throws Exception {
        super.onMessage(event);

        // Clean channel name, should match the session login name.
        String channel = Objects.requireNonNull(event.getChannel().getName()).replace("#", "");

        if (sessionMapService.sessionMap().containsKey(channel)) {
            IrcPayload ircPayload = IrcPayload.builder()
                    .channel(channel)
                    .nick(Objects.requireNonNull(event.getUser()).getNick())
                    .userId(event.getUser().getUserId().toString())
                    .message(event.getMessage())
                    .timestamp(Instant.now())
                    .session(sessionMapService.sessionMap().get(channel))
                    .build();

            kafkaProducerService.sendMessage(ircPayload);
        } else {
            log.info("No session for channel: {}", channel);
        }
    }
}
