package space.forloop.irc.producer.tasks;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pircbotx.PircBotX;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotStarter {

    private final PircBotX pircBotX;

    @PostConstruct
    public void startBot() {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting IRC bot...");
                pircBotX.startBot();
            } catch (Exception e) {
                log.error("Failed to start bot", e);
            }
        });
    }
}
