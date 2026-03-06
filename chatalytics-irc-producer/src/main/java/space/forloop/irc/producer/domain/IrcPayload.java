package space.forloop.irc.producer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.pircbotx.hooks.events.MessageEvent;
import space.forloop.chatalytics.data.domain.SessionWithUser;

import java.time.Instant;
import java.util.Objects;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IrcPayload {
    private String channel;
    private String nick;
    private String userId;
    private String message;
    private Instant timestamp;

    private SessionWithUser session;

    public static IrcPayload fromMessageEvent(MessageEvent messageEvent) {
        final String channel = messageEvent.getChannel().getName().split("#")[1];
        final String nick = Objects.requireNonNull(messageEvent.getUser()).getNick();
        final String userId = messageEvent.getUser().getUserId().toString();
        final String message = messageEvent.getMessage();

        return IrcPayload.builder()
                .channel(channel)
                .nick(nick)
                .userId(userId)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
