package space.forloop.irc.producer.services;

import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.SessionWithUser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionMapServiceImpl implements SessionMapService {

    private final Map<String, SessionWithUser> sessionMap = new ConcurrentHashMap<>();

    @Override
    public Map<String, SessionWithUser> sessionMap() {
        return sessionMap;
    }
}
