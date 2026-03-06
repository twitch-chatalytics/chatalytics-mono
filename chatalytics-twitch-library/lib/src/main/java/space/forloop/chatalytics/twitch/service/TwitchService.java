package space.forloop.chatalytics.twitch.service;

import space.forloop.chatalytics.twitch.model.StreamData;
import space.forloop.chatalytics.twitch.model.TwitchClipData;
import space.forloop.chatalytics.twitch.model.TwitchUser;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface TwitchService {

    List<TwitchUser> findUsersByLogin(Collection<String> usernames);

    TwitchUser findUserByLogin(String username);

    Set<StreamData> batchCheckUserOnlineStatus(List<String> logins);

    Set<StreamData> findAllOnlineStreams(List<TwitchUser> users);

    List<StreamData> findTopOnlineUsers();

    List<TwitchClipData> findClips(String broadcasterId, Instant startedAt, Instant endedAt, int limit);
}
