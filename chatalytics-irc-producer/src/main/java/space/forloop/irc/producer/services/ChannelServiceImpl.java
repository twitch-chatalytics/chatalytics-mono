package space.forloop.irc.producer.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.SessionWithUser;

import java.util.Collections;
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

        // Always update the session map so we never serve stale session IDs
        sessionMapService.sessionMap().put(sessionWithUser.login(), sessionWithUser);

        if (joinedChannels.add(channelName)) {
            sendRawCommand(JOIN_COMMAND, channelName);
        }
    }

    @Override
    public void leaveChannel(final String channel) {
        final String channelName = getChannelName(channel);

        if (joinedChannels.remove(channelName)) {
            sessionMapService.sessionMap().remove(channel);
            sendRawCommand(PART_COMMAND, channelName);
        }
    }

    @Override
    public void clearJoinedChannels() {
        joinedChannels.clear();
    }

    @Override
    public Set<String> getJoinedChannels() {
        return Collections.unmodifiableSet(joinedChannels);
    }

    private void sendRawCommand(String command, String channelName) {
        if (!botService.getBot().isConnected()) {
            log.warn("Skipping {} {} — bot not connected (will rejoin on reconnect)", command, channelName);
            if (JOIN_COMMAND.equals(command)) {
                joinedChannels.remove(channelName);
            }
            return;
        }

        try {
            log.info("{} {}", command, channelName);
            botService.getBot()
                    .sendRaw()
                    .rawLine(String.format("%s %s", command, channelName));
        } catch (Exception e) {
            log.warn("Failed to send {} {} — {}", command, channelName, e.getMessage());
            if (JOIN_COMMAND.equals(command)) {
                joinedChannels.remove(channelName);
            }
        }
    }
}
