package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.SessionMetrics;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.generated.tables.pojos.SessionSummary;

import java.util.List;

public interface SessionSummaryRepository {

    List<SessionSummary> findAllSessions(List<Long> sessionIds, long twitchId);

    void write(Session session, SessionMetrics sessionMetrics, boolean partial);

}
