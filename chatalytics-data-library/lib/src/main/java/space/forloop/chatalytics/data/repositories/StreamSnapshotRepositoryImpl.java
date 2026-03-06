package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.StreamSnapshot;
import space.forloop.chatalytics.data.domain.TopGame;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RequiredArgsConstructor
public class StreamSnapshotRepositoryImpl implements StreamSnapshotRepository {

    private static final Table<?> TABLE = DSL.table("twitch.stream_snapshot");
    private static final Field<Long> ID = DSL.field("id", Long.class);
    private static final Field<Long> SESSION_ID = DSL.field("session_id", Long.class);
    private static final Field<Long> TWITCH_ID = DSL.field("twitch_id", Long.class);
    private static final Field<OffsetDateTime> TIMESTAMP = DSL.field("timestamp", SQLDataType.TIMESTAMPWITHTIMEZONE);
    private static final Field<String> GAME_NAME = DSL.field("game_name", String.class);
    private static final Field<String> TITLE = DSL.field("title", String.class);
    private static final Field<Integer> VIEWER_COUNT = DSL.field("viewer_count", Integer.class);

    private final DSLContext dsl;

    @Override
    @Transactional
    public void write(long sessionId, long twitchId, String gameName, String title, int viewerCount) {
        dsl.insertInto(TABLE)
                .set(SESSION_ID, sessionId)
                .set(TWITCH_ID, twitchId)
                .set(GAME_NAME, gameName)
                .set(TITLE, title)
                .set(VIEWER_COUNT, viewerCount)
                .execute();
    }

    @Override
    public List<StreamSnapshot> findBySessionId(long sessionId) {
        return dsl.select(ID, SESSION_ID, TWITCH_ID, TIMESTAMP, GAME_NAME, TITLE, VIEWER_COUNT)
                .from(TABLE)
                .where(SESSION_ID.eq(sessionId))
                .orderBy(TIMESTAMP.asc())
                .fetch(r -> new StreamSnapshot(
                        r.get(ID),
                        r.get(SESSION_ID),
                        r.get(TWITCH_ID),
                        r.get(TIMESTAMP).toInstant(),
                        r.get(GAME_NAME),
                        r.get(TITLE),
                        r.get(VIEWER_COUNT)
                ));
    }

    @Override
    public List<StreamSnapshot> findByTwitchId(long twitchId, Instant from, Instant to) {
        var query = dsl.select(ID, SESSION_ID, TWITCH_ID, TIMESTAMP, GAME_NAME, TITLE, VIEWER_COUNT)
                .from(TABLE)
                .where(TWITCH_ID.eq(twitchId));

        if (from != null) {
            query = query.and(TIMESTAMP.ge(OffsetDateTime.ofInstant(from, ZoneOffset.UTC)));
        }
        if (to != null) {
            query = query.and(TIMESTAMP.le(OffsetDateTime.ofInstant(to, ZoneOffset.UTC)));
        }

        return query.orderBy(TIMESTAMP.asc())
                .fetch(r -> new StreamSnapshot(
                        r.get(ID),
                        r.get(SESSION_ID),
                        r.get(TWITCH_ID),
                        r.get(TIMESTAMP).toInstant(),
                        r.get(GAME_NAME),
                        r.get(TITLE),
                        r.get(VIEWER_COUNT)
                ));
    }

    @Override
    public List<TopGame> topGamesByTwitchId(long twitchId, int limit) {
        return dsl.select(GAME_NAME.as("gameName"), DSL.countDistinct(SESSION_ID).as("sessionCount"))
                .from(TABLE)
                .where(TWITCH_ID.eq(twitchId))
                .and(GAME_NAME.isNotNull())
                .groupBy(GAME_NAME)
                .orderBy(DSL.countDistinct(SESSION_ID).desc())
                .limit(limit)
                .fetchInto(TopGame.class);
    }
}
