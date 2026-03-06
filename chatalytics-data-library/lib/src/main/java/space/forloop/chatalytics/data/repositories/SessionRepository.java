package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.SessionSummaryView;
import space.forloop.chatalytics.data.domain.SessionWithUser;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Optional<SessionWithUser> findByIdWithUser(long sessionId);

    List<SessionWithUser> findAllOpenSessionsWithUser();

    List<Session> findUnprocessedCompletedSessions();

    List<Session> findUnprocessedActiveSessions();

    Session write(Session session);

    List<Session> findAllOpenSessions();

    Session updateSessionEndTime(long sessionId, Instant endTime);

    List<Session> findAllByUserId(long userId);

    long countByUserId(long userId);

    List<SessionSummaryView> findSessionsWithStats(long twitchId, int limit);

    Double avgStreamDurationMinutes(Long twitchId);
}
