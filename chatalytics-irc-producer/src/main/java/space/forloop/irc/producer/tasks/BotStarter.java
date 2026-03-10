package space.forloop.irc.producer.tasks;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.data.domain.SessionWithUser;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.irc.producer.services.ChannelService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotStarter {

    private final PircBotX pircBotX;
    private final ChannelService channelService;
    private final SessionRepository sessionRepository;

    private volatile CountDownLatch connectionReady = new CountDownLatch(1);

    @PostConstruct
    public void startBot() {
        pircBotX.getConfiguration().getListenerManager().addListener(new ListenerAdapter() {
            @Override
            public void onConnect(ConnectEvent event) {
                log.info("IRC bot connected to Twitch");
                connectionReady.countDown();
                rejoinAllChannels();
            }

            @Override
            public void onDisconnect(DisconnectEvent event) {
                log.warn("IRC bot disconnected — preparing for reconnect");
                connectionReady = new CountDownLatch(1);
                channelService.clearJoinedChannels();
            }
        });

        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting IRC bot...");
                pircBotX.startBot();
            } catch (Exception e) {
                log.error("Failed to start bot", e);
            }
        });
    }

    public boolean awaitConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return connectionReady.await(timeout, unit);
    }

    private void rejoinAllChannels() {
        List<SessionWithUser> openSessions = sessionRepository.findAllOpenSessionsWithUser();
        log.info("Re-joining {} channels after connect", openSessions.size());
        openSessions.forEach(channelService::joinChannel);
    }
}
