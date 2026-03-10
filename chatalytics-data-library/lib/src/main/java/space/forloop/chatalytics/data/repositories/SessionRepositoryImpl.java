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
                .where(SESSION.CHANNEL_ID.eq(userId))
                .fetchSingleInto(Long.class);
    }

    @Override
    public List<Session> findAllByUserId(long userId) {
        return dsl
                .select(SESSION.asterisk())
                .from(SESSION)
                .leftJoin(MESSAGE)
                .on(SESSION.ID.eq(MESSAGE.SESSION_ID))
                .where(SESSION.CHANNEL_ID.eq(userId))
                .groupBy(SESSION.fields())
                .having(DSL.count(MESSAGE.ID).gt(0))
                .orderBy(SESSION.START_TIME.asc())
                .fetchInto(Session.class);
    }

    @Override
    public Optional<SessionWithUser> findByIdWithUser(long sessionId) {
        return dsl.select(SESSION.asterisk(), USER.LOGIN)
                .from(SESSION)
                .join(USER).on(SESSION.CHANNEL_ID.eq(USER.ID))
                .where(SESSION.ID.eq(sessionId))
                .fetchOptionalInto(SessionWithUser.class);

    }

    @Override
    public List<SessionWithUser> findAllOpenSessionsWithUser() {
        return dsl.select(SESSION.asterisk(), USER.LOGIN)
                .from(SESSION)
                .join(USER).on(SESSION.CHANNEL_ID.eq(USER.ID))
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
    public Double avgStreamDurationMinutes(Long channelId) {
        return dsl.select(
                DSL.avg(DSL.field(
                        "EXTRACT(EPOCH FROM (end_time - start_time)) / 60",
                        Double.class
                ))
        )
        .from(SESSION)
        .where(SESSION.CHANNEL_ID.eq(channelId))
        .and(SESSION.END_TIME.isNotNull())
        .fetchOneInto(Double.class);
    }

    @Override
    public List<SessionSummaryView> findSessionsWithStats(long channelId, int limit) {
        return findSessionsWithStats(channelId, limit, null, null, null, null);
    }

    @Override
    public List<SessionSummaryView> findSessionsWithStats(
            long channelId, int limit,
            Instant from, Instant to,
            Instant beforeStartTime, Long beforeId) {

        List<Condition> conditions = new ArrayList<>();
        conditions.add(SESSION.CHANNEL_ID.eq(channelId));

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

        // LATERAL join: latest game_name for each session (only runs per-row, not full table scan)
        Field<String> lastGameName = DSL.field(DSL.name("latest_snap", "game_name"), String.class);

        // LATERAL join: peak viewer count for each session
        Field<Integer> peakViewerCount = DSL.field(DSL.name("peak_viewers", "peak_viewer_count"), Integer.class);

        // LATERAL join: message stats computed directly from message table
        Field<Long> totalMessages = DSL.field(DSL.name("msg_stats", "total_messages"), Long.class);
        Field<Long> totalChatters = DSL.field(DSL.name("msg_stats", "total_chatters"), Long.class);

        Field<Long> durationMinutes = DSL.field(
                "EXTRACT(EPOCH FROM (COALESCE({0}, NOW()) - {1})) / 60",
                Long.class,
                SESSION.END_TIME, SESSION.START_TIME
        );

        return dsl.select(
                        SESSION.ID.as("sessionId"),
                        SESSION.CHANNEL_ID.as("channelId"),
                        SESSION.START_TIME.as("startTime"),
                        SESSION.END_TIME.as("endTime"),
                        DSL.coalesce(totalMessages, 0L).as("totalMessages"),
                        DSL.coalesce(totalChatters, 0L).as("totalChatters"),
                        lastGameName.as("lastGameName"),
                        peakViewerCount.as("peakViewerCount"),
                        DSL.field(
                                "CASE WHEN {0} > 0 THEN COALESCE({1}, 0)::double precision / {0} ELSE 0 END",
                                Double.class,
                                durationMinutes, totalMessages
                        ).as("messagesPerMinute"),
                        durationMinutes.as("durationMinutes")
                )
                .from(SESSION)
                .leftJoin(DSL.table(
                        "LATERAL (SELECT COUNT(*) AS total_messages, COUNT(DISTINCT author) AS total_chatters FROM chat.message WHERE session_id = chat.session.id)"
                ).asTable("msg_stats")).on(DSL.trueCondition())
                .leftJoin(DSL.table(
                        "LATERAL (SELECT game_name FROM chat.stream_snapshot WHERE session_id = chat.session.id ORDER BY \"timestamp\" DESC LIMIT 1)"
                ).asTable("latest_snap")).on(DSL.trueCondition())
                .leftJoin(DSL.table(
                        "LATERAL (SELECT MAX(viewer_count) AS peak_viewer_count FROM chat.stream_snapshot WHERE session_id = chat.session.id)"
                ).asTable("peak_viewers")).on(DSL.trueCondition())
                .where(conditions)
                .orderBy(SESSION.START_TIME.desc(), SESSION.ID.desc())
                .limit(limit)
                .fetchInto(SessionSummaryView.class);
    }
}
