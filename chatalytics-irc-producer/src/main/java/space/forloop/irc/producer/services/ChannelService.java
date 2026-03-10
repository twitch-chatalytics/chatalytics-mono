package space.forloop.irc.producer.services;

import space.forloop.chatalytics.data.domain.SessionWithUser;

import java.util.Set;

public interface ChannelService {
    void joinChannel(SessionWithUser sessionWithUser);

    void leaveChannel(String channel);

    void clearJoinedChannels();

    Set<String> getJoinedChannels();
}
