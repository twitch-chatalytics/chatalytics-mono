package space.forloop.chatalytics.twitch.service;

import space.forloop.chatalytics.twitch.model.StreamData;
import space.forloop.chatalytics.twitch.model.TwitchUser;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface TwitchService {

    List<TwitchUser> findUsersByLogin(Collection<String> usernames);

    TwitchUser findUserByLogin(String username);

    Set<StreamData> batchCheckUserOnlineStatus(List<String> logins);

    List<String> findAllOnlineUsers(List<TwitchUser> users);

    Set<StreamData> findAllOnlineStreams(List<TwitchUser> users);

    List<StreamData> findTopOnlineUsers();
}
