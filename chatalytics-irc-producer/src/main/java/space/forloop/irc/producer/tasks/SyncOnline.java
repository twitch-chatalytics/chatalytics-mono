package space.forloop.irc.producer.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.data.domain.SessionWithUser;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.irc.producer.services.ChannelService;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncOnline {

    private final SessionRepository sessionRepository;
    private final ChannelService channelService;
    private final BotStarter botStarter;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            boolean connected = botStarter.awaitConnection(60, TimeUnit.SECONDS);
            if (!connected) {
                log.warn("Timed out waiting for IRC connection — channels will be joined on connect");
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for IRC connection");
            return;
        }

        List<SessionWithUser> openSessions = sessionRepository.findAllOpenSessionsWithUser();
        log.info("Joining {} channels on startup", openSessions.size());
        openSessions.forEach(channelService::joinChannel);
    }
}
