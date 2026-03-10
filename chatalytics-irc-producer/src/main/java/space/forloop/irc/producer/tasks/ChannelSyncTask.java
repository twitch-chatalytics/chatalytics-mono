package space.forloop.irc.producer.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.data.domain.SessionWithUser;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.irc.producer.services.ChannelService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelSyncTask {

    private final SessionRepository sessionRepository;
    private final ChannelService channelService;

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void syncChannels() {
        List<SessionWithUser> openSessions = sessionRepository.findAllOpenSessionsWithUser();

        Set<String> expectedChannels = openSessions.stream()
                .map(s -> "#" + s.login())
                .collect(Collectors.toSet());

        // Join any missing channels (also refreshes session map)
        openSessions.forEach(channelService::joinChannel);

        // Leave channels that no longer have an open session
        Set<String> currentChannels = channelService.getJoinedChannels();
        int left = 0;
        for (String channel : currentChannels) {
            if (!expectedChannels.contains(channel)) {
                channelService.leaveChannel(channel.substring(1)); // strip #
                left++;
            }
        }

        log.info("Channel sync: expected={}, joined={}, left={}",
                expectedChannels.size(), currentChannels.size(), left);
    }
}
