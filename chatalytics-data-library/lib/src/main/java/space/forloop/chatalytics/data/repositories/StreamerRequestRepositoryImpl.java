package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import space.forloop.chatalytics.data.domain.StreamerRequestSummary;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StreamerRequestRepositoryImpl implements StreamerRequestRepository {

    private final DSLContext dsl;

    private static final org.jooq.Table<?> STREAMER_REQUEST = DSL.table("chat.streamer_request");
    private static final org.jooq.Table<?> USER = DSL.table("chat.user");

    private static final org.jooq.Field<String> STREAMER_LOGIN = DSL.field("sr.streamer_login", String.class);
    private static final org.jooq.Field<Long> STREAMER_ID = DSL.field("sr.streamer_id", Long.class);
    private static final org.jooq.Field<String> DISPLAY_NAME = DSL.field("sr.display_name", String.class);
    private static final org.jooq.Field<String> PROFILE_IMAGE_URL = DSL.field("sr.profile_image_url", String.class);
    private static final org.jooq.Field<Long> REQUESTED_BY = DSL.field("requested_by", Long.class);
    private static final org.jooq.Field<Long> USER_ID = DSL.field("u.id", Long.class);

    @Override
    public void save(String streamerLogin, Long streamerId, String displayName, String profileImageUrl, long requestedBy) {
        dsl.insertInto(STREAMER_REQUEST)
                .set(DSL.field("streamer_login", String.class), streamerLogin)
                .set(DSL.field("streamer_id", Long.class), streamerId)
                .set(DSL.field("display_name", String.class), displayName)
                .set(DSL.field("profile_image_url", String.class), profileImageUrl)
                .set(REQUESTED_BY, requestedBy)
                .onDuplicateKeyIgnore()
                .execute();
    }

    @Override
    public long countByStreamerLogin(String streamerLogin) {
        return dsl.selectCount()
                .from(STREAMER_REQUEST)
                .where(DSL.field("streamer_login", String.class).eq(streamerLogin))
                .fetchOne(0, long.class);
    }

    @Override
    public List<StreamerRequestSummary> findAllPending() {
        var sr = STREAMER_REQUEST.as("sr");
        var u = USER.as("u");
        var voteCount = DSL.count().as("vote_count");

        return dsl.select(STREAMER_LOGIN, STREAMER_ID, DISPLAY_NAME, PROFILE_IMAGE_URL, voteCount)
                .from(sr)
                .leftJoin(u).on(DSL.field("sr.streamer_login", String.class).eq(DSL.field("u.login", String.class)))
                .where(USER_ID.isNull())
                .groupBy(STREAMER_LOGIN, STREAMER_ID, DISPLAY_NAME, PROFILE_IMAGE_URL)
                .orderBy(voteCount.desc())
                .fetch(r -> new StreamerRequestSummary(
                        r.get(STREAMER_LOGIN),
                        r.get(STREAMER_ID),
                        r.get(DISPLAY_NAME),
                        r.get(PROFILE_IMAGE_URL),
                        r.get(voteCount)
                ));
    }

    @Override
    public boolean existsByStreamerLoginAndRequestedBy(String streamerLogin, long requestedBy) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(STREAMER_REQUEST)
                        .where(DSL.field("streamer_login", String.class).eq(streamerLogin))
                        .and(REQUESTED_BY.eq(requestedBy))
        );
    }

    @Override
    public List<StreamerRequestSummary> findPendingPaged(int limit, int offset) {
        var sr = STREAMER_REQUEST.as("sr");
        var u = USER.as("u");
        var voteCount = DSL.count().as("vote_count");

        return dsl.select(STREAMER_LOGIN, STREAMER_ID, DISPLAY_NAME, PROFILE_IMAGE_URL, voteCount)
                .from(sr)
                .leftJoin(u).on(DSL.field("sr.streamer_login", String.class).eq(DSL.field("u.login", String.class)))
                .where(USER_ID.isNull())
                .groupBy(STREAMER_LOGIN, STREAMER_ID, DISPLAY_NAME, PROFILE_IMAGE_URL)
                .orderBy(voteCount.desc())
                .limit(limit)
                .offset(offset)
                .fetch(r -> new StreamerRequestSummary(
                        r.get(STREAMER_LOGIN),
                        r.get(STREAMER_ID),
                        r.get(DISPLAY_NAME),
                        r.get(PROFILE_IMAGE_URL),
                        r.get(voteCount)
                ));
    }

    @Override
    public long countPending() {
        var sr = STREAMER_REQUEST.as("sr");
        var u = USER.as("u");

        return dsl.select(DSL.countDistinct(DSL.field("sr.streamer_login")))
                .from(sr)
                .leftJoin(u).on(DSL.field("sr.streamer_login", String.class).eq(DSL.field("u.login", String.class)))
                .where(USER_ID.isNull())
                .fetchOne(0, long.class);
    }
}
