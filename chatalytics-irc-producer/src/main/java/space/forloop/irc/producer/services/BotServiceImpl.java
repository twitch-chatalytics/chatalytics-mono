package space.forloop.irc.producer.services;

import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BotServiceImpl implements BotService {

    private final PircBotX pircBotX;

    @Override
    public PircBotX getBot() {
        return this.pircBotX;
    }
}