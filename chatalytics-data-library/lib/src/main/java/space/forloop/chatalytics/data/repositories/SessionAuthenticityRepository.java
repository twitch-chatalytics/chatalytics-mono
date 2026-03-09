package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.AuthenticityTrendPoint;
import space.forloop.chatalytics.data.domain.SessionAuthenticity;

import java.util.List;
import java.util.Optional;

public interface SessionAuthenticityRepository {

    Optional<SessionAuthenticity> findBySessionId(long sessionId);

    void save(SessionAuthenticity authenticity);

    List<Long> findSessionIdsWithoutAuthenticity();

    List<SessionAuthenticity> findByTwitchId(long twitchId, int limit, int offset);

    List<AuthenticityTrendPoint> findTrendByTwitchId(long twitchId, int limit);

    List<Long> findTwitchIdsWithoutChannelRollup();
}
