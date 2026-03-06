package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.SessionSummaryView;
import space.forloop.chatalytics.data.domain.SessionWithUser;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;

import java.time.Instant;
import java.time.OffsetDateTime;
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
    public List<SessionSummaryView> findSessionsWithStats(long twitchId, int limit) {
        Table<?> snapshot = DSL.table("twitch.stream_snapshot");
        Field<Long> snapSessionId = DSL.field("twitch.stream_snapshot.session_id", Long.class);
        Field<String> snapGameName = DSL.field("twitch.stream_snapshot.game_name", String.class);
        Field<OffsetDateTime> snapTimestamp = DSL.field("twitch.stream_snapshot.timestamp", SQLDataType.TIMESTAMPWITHTIMEZONE);

        // Subquery: latest game_name per session from snapshots
        var latestSnapshot = DSL.select(
                        snapSessionId.as("ss_session_id"),
                        snapGameName.as("last_game_name")
                )
                .from(snapshot)
                .where(snapTimestamp.eq(
                        DSL.select(DSL.max(snapTimestamp))
                                .from(snapshot.as("s2"))
                                .where(DSL.field("s2.session_id", Long.class).eq(snapSessionId))
                ))
                .asTable("latest_snap");

        return dsl.select(
                        SESSION.ID.as("sessionId"),
                        SESSION.TWITCH_ID.as("twitchId"),
                        SESSION.START_TIME.as("startTime"),
                        SESSION.END_TIME.as("endTime"),
                        DSL.count(MESSAGE.ID).as("totalMessages"),
                        DSL.countDistinct(MESSAGE.AUTHOR).as("totalChatters"),
                        latestSnapshot.field("last_game_name", String.class).as("lastGameName")
                )
                .from(SESSION)
                .leftJoin(MESSAGE).on(SESSION.ID.eq(MESSAGE.SESSION_ID))
                .leftJoin(latestSnapshot).on(SESSION.ID.eq(latestSnapshot.field("ss_session_id", Long.class)))
                .where(SESSION.TWITCH_ID.eq(twitchId))
                .groupBy(SESSION.ID, SESSION.TWITCH_ID, SESSION.START_TIME, SESSION.END_TIME,
                        latestSnapshot.field("last_game_name", String.class))
                .having(DSL.count(MESSAGE.ID).gt(0))
                .orderBy(SESSION.START_TIME.desc())
                .limit(limit)
                .fetchInto(SessionSummaryView.class);
    }
}
