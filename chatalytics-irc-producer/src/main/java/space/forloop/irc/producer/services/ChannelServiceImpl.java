package space.forloop.irc.producer.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.SessionWithUser;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    public static final String JOIN_COMMAND = "JOIN";
    public static final String PART_COMMAND = "PART";
    public final SessionMapService sessionMapService;
    private final BotService botService;

    // Keep track of joined channels in a Set for O(1) lookup
    private final Set<String> joinedChannels = ConcurrentHashMap.newKeySet();

    private static String getChannelName(final String channel) {
        return "#" + channel;
    }

    @Override
    public void joinChannel(final SessionWithUser sessionWithUser) {
        final String channelName = getChannelName(sessionWithUser.login());

        // O(1) lookup instead of streaming through all channels
        if (joinedChannels.add(channelName)) {  // returns true if channel wasn't already in set
            sessionMapService.sessionMap().put(sessionWithUser.login(), sessionWithUser);
            sendRawCommand(JOIN_COMMAND, channelName);
        }
    }

    @Override
    public void leaveChannel(final String channel) {
        final String channelName = getChannelName(channel);

        if (joinedChannels.remove(channelName)) {  // returns true if channel was in set
            sendRawCommand(PART_COMMAND, channelName);
        }
    }

    private void sendRawCommand(String command, String channelName) {
        log.info("{} {}", command, channelName);

        botService.getBot()
                .sendRaw()
                .rawLine(String.format("%s %s", command, channelName));
    }
}