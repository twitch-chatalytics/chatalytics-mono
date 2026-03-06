package space.forloop.irc.producer.services;

import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BotService {

    private final PircBotX pircBotX;

    public PircBotX getBot() {
        return this.pircBotX;
    }
}
