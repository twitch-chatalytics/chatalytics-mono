package space.forloop.irc.producer.services;

import space.forloop.chatalytics.data.domain.SessionWithUser;

public interface ChannelService {
    void joinChannel(SessionWithUser sessionWithUser);

    void leaveChannel(String channel);
}
