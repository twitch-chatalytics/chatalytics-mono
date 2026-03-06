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

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncOnline {

    private final SessionRepository sessionRepository;

    private final ChannelService channelService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        List<SessionWithUser> openSessions = sessionRepository.findAllOpenSessionsWithUser();

        openSessions.forEach(channelService::joinChannel);
    }
}