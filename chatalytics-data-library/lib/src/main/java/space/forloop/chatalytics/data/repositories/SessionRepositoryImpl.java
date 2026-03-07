package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.SessionSummaryView;
import space.forloop.chatalytics.data.domain.SessionWithUser;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static space.forloop.chatalytics.data.generated.Tables.*;

@Service
@RequiredArgsConstructor
public class SessionRepositoryImpl implements SessionRepository {

    private final DSLContext dsl;

    @Override
    public long countByUserId(long userId) {
        return dsl
                .selectCount()
                .from(SESSION)
                .where(SESSION.TWITCH_ID.eq(userId))
                .fetchSingleInto(Long.class);
    }

    @Override
    public List<Session> findAllByUserId(long userId) {
        return dsl
                .select(SESSION.asterisk())
                .from(SESSION)
                .leftJoin(MESSAGE)
                .on(SESSION.ID.eq(MESSAGE.SESSION_ID))
                .where(SESSION.TWITCH_ID.eq(userId))
                .groupBy(SESSION.fields())
                .having(DSL.count(MESSAGE.ID).gt(0))
                .orderBy(SESSION.START_TIME.asc())
                .fetchInto(Session.class);
    }

    @Override
    public Optional<SessionWithUser> findByIdWithUser(long sessionId) {
        return dsl.select(SESSION.asterisk(), USER.LOGIN)
                .from(SESSION)
                .join(USER).on(SESSION.TWITCH_ID.eq(USER.ID))
                .where(SESSION.ID.eq(sessionId))
                .fetchOptionalInto(SessionWithUser.class);

    }

    @Override
    public List<SessionWithUser> findAllOpenSessionsWithUser() {
        return dsl.select(SESSION.asterisk(), USER.LOGIN)
                .from(SESSION)
                .join(USER).on(SESSION.TWITCH_ID.eq(USER.ID))
                .where(SESSION.END_TIME.isNull())
                .fetchInto(SessionWithUser.class);
    }

    @Override
    public List<Session> findAllOpenSessions() {
        return dsl.select(SESSION.asterisk())
                .from(SESSION)
                .where(SESSION.END_TIME.isNull())
                .fetchInto(Session.class);
    }

    @Override
    public List<Session> findUnprocessedCompletedSessions() {
        return dsl
                .select()
                .from(SESSION)
                .where(SESSION.END_TIME.isNotNull())
                .andNotExists(
                        dsl.selectFrom(ROLLUP_HISTORY)
                                .where(ROLLUP_HISTORY.SESSION_ID.eq(SESSION.ID))
                                .and(ROLLUP_HISTORY.COMPLETE.eq(true))
                )
                .fetchInto(Session.class);
    }

    @Override
    public List<Session> findUnprocessedActiveSessions() {
        return dsl
                .select()
                .from(SESSION)
                .where(SESSION.END_TIME.isNull())
                .andNotExists(
                        dsl.selectFrom(ROLLUP_HISTORY)
                                .where(ROLLUP_HISTORY.SESSION_ID.eq(SESSION.ID))
                                .and(ROLLUP_HISTORY.COMPLETE.eq(true))
                )
                .fetchInto(Session.class);
    }

    @Override
    @Transactional
    public Session write(Session session) {
        return dsl.insertInto(SESSION)
                .set(dsl.newRecord(SESSION, session))
                .returning()
                .fetchOneInto(Session.class);
    }

    @Override
    @Transactional
    public Session updateSessionEndTime(long sessionId, Instant endTime) {
        return dsl.update(SESSION)
                .set(SESSION.END_TIME, endTime)
                .where(SESSION.ID.eq(sessionId))
                .returning()
                .fetchOneInto(Session.class);
    }

    @Override
    public Double avgStreamDurationMinutes(Long twitchId) {
        return dsl.select(
                DSL.avg(DSL.field(
                        "EXTRACT(EPOCH FROM (end_time - start_time)) / 60",
                        Double.class
                ))
        )
        .from(SESSION)
        .where(SESSION.TWITCH_ID.eq(twitchId))
        .and(SESSION.END_TIME.isNotNull())
        .fetchOneInto(Double.class);
    }

    @Override
    public List<SessionSummaryView> findSessionsWithStats(long twitchId, int limit) {
        return findSessionsWithStats(twitchId, limit, null, null, null, null);
    }

    @Override
    public List<SessionSummaryView> findSessionsWithStats(
            long twitchId, int limit,
            Instant from, Instant to,
            Instant beforeStartTime, Long beforeId) {

        List<Condition> conditions = new ArrayList<>();
        conditions.add(SESSION.TWITCH_ID.eq(twitchId));

        if (from != null) {
            conditions.add(SESSION.START_TIME.ge(from));
        }
        if (to != null) {
            conditions.add(SESSION.START_TIME.le(to));
        }
        if (beforeStartTime != null && beforeId != null) {
            conditions.add(
                    DSL.row(SESSION.START_TIME, SESSION.ID).lessThan(DSL.row(DSL.val(beforeStartTime), DSL.val(beforeId)))
            );
        }

        // Subquery: latest game_name per session using DISTINCT ON (PostgreSQL)
        var latestSnapshot = DSL.table(
                "(SELECT DISTINCT ON (session_id) session_id AS ss_session_id, game_name AS last_game_name " +
                "FROM twitch.stream_snapshot ORDER BY session_id, \"timestamp\" DESC)"
        ).asTable("latest_snap");

        // Subquery: peak viewer count per session
        var peakViewers = DSL.table(
                "(SELECT session_id AS pv_session_id, MAX(viewer_count) AS peak_viewer_count " +
                "FROM twitch.stream_snapshot GROUP BY session_id)"
        ).asTable("peak_viewers");

        Field<Long> snapSessionId = DSL.field(DSL.name("latest_snap", "ss_session_id"), Long.class);
        Field<String> lastGameName = DSL.field(DSL.name("latest_snap", "last_game_name"), String.class);
        Field<Long> pvSessionId = DSL.field(DSL.name("peak_viewers", "pv_session_id"), Long.class);
        Field<Integer> peakViewerCount = DSL.field(DSL.name("peak_viewers", "peak_viewer_count"), Integer.class);

        Field<Long> durationMinutes = DSL.field(
                "EXTRACT(EPOCH FROM (COALESCE({0}, NOW()) - {1})) / 60",
                Long.class,
                SESSION.END_TIME, SESSION.START_TIME
        );

        return dsl.select(
                        SESSION.ID.as("sessionId"),
                        SESSION.TWITCH_ID.as("twitchId"),
                        SESSION.START_TIME.as("startTime"),
                        SESSION.END_TIME.as("endTime"),
                        DSL.count(MESSAGE.ID).as("totalMessages"),
                        DSL.countDistinct(MESSAGE.AUTHOR).as("totalChatters"),
                        lastGameName.as("lastGameName"),
                        peakViewerCount.as("peakViewerCount"),
                        DSL.when(durationMinutes.gt(0L),
                                DSL.count(MESSAGE.ID).cast(Double.class).div(durationMinutes.cast(Double.class))
                        ).as("messagesPerMinute"),
                        durationMinutes.as("durationMinutes")
                )
                .from(SESSION)
                .leftJoin(MESSAGE).on(SESSION.ID.eq(MESSAGE.SESSION_ID))
                .leftJoin(latestSnapshot).on(SESSION.ID.eq(snapSessionId))
                .leftJoin(peakViewers).on(SESSION.ID.eq(pvSessionId))
                .where(conditions)
                .groupBy(SESSION.ID, SESSION.TWITCH_ID, SESSION.START_TIME, SESSION.END_TIME,
                        lastGameName, peakViewerCount, durationMinutes)
                .orderBy(SESSION.START_TIME.desc(), SESSION.ID.desc())
                .limit(limit)
                .fetchInto(SessionSummaryView.class);
    }
}
