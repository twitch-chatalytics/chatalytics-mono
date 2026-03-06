package space.forloop.irc.producer.services;

import space.forloop.chatalytics.data.domain.SessionWithUser;

import java.util.Map;

public interface SessionMapService {

    Map<String, SessionWithUser> sessionMap();

}
