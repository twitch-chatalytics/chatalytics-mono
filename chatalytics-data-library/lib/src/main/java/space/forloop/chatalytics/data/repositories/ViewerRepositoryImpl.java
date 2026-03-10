package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import space.forloop.chatalytics.data.domain.Viewer;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ViewerRepositoryImpl implements ViewerRepository {

    private final DSLContext dsl;

    private static final org.jooq.Table<?> VIEWER = DSL.table("chat.viewer");
    private static final org.jooq.Field<Long> CHANNEL_ID = DSL.field("channel_id", Long.class);
    private static final org.jooq.Field<String> LOGIN = DSL.field("login", String.class);
    private static final org.jooq.Field<String> DISPLAY_NAME = DSL.field("display_name", String.class);
    private static final org.jooq.Field<String> PROFILE_IMAGE_URL = DSL.field("profile_image_url", String.class);
    private static final org.jooq.Field<Instant> CREATED_AT = DSL.field("created_at", Instant.class);

    @Override
    public Optional<Viewer> findByChannelId(long channelId) {
        return dsl.select(CHANNEL_ID, LOGIN, DISPLAY_NAME, PROFILE_IMAGE_URL, CREATED_AT)
                .from(VIEWER)
                .where(CHANNEL_ID.eq(channelId))
                .fetchOptional(r -> new Viewer(
                        r.get(CHANNEL_ID),
                        r.get(LOGIN),
                        r.get(DISPLAY_NAME),
                        r.get(PROFILE_IMAGE_URL),
                        r.get(CREATED_AT)
                ));
    }

    @Override
    public Viewer save(Viewer viewer) {
        dsl.insertInto(VIEWER)
                .set(CHANNEL_ID, viewer.channelId())
                .set(LOGIN, viewer.login())
                .set(DISPLAY_NAME, viewer.displayName())
                .set(PROFILE_IMAGE_URL, viewer.profileImageUrl())
                .onConflict(CHANNEL_ID)
                .doUpdate()
                .set(LOGIN, DSL.field("EXCLUDED.login", String.class))
                .set(DISPLAY_NAME, DSL.field("EXCLUDED.display_name", String.class))
                .set(PROFILE_IMAGE_URL, DSL.field("EXCLUDED.profile_image_url", String.class))
                .execute();
        return viewer;
    }
}
