package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.AuthenticityTrendPoint;
import space.forloop.chatalytics.data.domain.SessionAuthenticity;

import java.util.List;
import java.util.Optional;

public interface SessionAuthenticityRepository {

    Optional<SessionAuthenticity> findBySessionId(long sessionId);

    void save(SessionAuthenticity authenticity);

    List<Long> findSessionIdsWithoutAuthenticity();

    List<SessionAuthenticity> findByChannelId(long channelId, int limit, int offset);

    List<AuthenticityTrendPoint> findTrendByChannelId(long channelId, int limit);

    List<Long> findChannelIdsWithoutChannelRollup();
}
