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

    private static final org.jooq.Table<?> VIEWER = DSL.table("twitch.viewer");
    private static final org.jooq.Field<Long> TWITCH_ID = DSL.field("twitch_id", Long.class);
    private static final org.jooq.Field<String> LOGIN = DSL.field("login", String.class);
    private static final org.jooq.Field<String> DISPLAY_NAME = DSL.field("display_name", String.class);
    private static final org.jooq.Field<String> PROFILE_IMAGE_URL = DSL.field("profile_image_url", String.class);
    private static final org.jooq.Field<Instant> CREATED_AT = DSL.field("created_at", Instant.class);

    @Override
    public Optional<Viewer> findByTwitchId(long twitchId) {
        return dsl.select(TWITCH_ID, LOGIN, DISPLAY_NAME, PROFILE_IMAGE_URL, CREATED_AT)
                .from(VIEWER)
                .where(TWITCH_ID.eq(twitchId))
                .fetchOptional(r -> new Viewer(
                        r.get(TWITCH_ID),
                        r.get(LOGIN),
                        r.get(DISPLAY_NAME),
                        r.get(PROFILE_IMAGE_URL),
                        r.get(CREATED_AT)
                ));
    }

    @Override
    public Viewer save(Viewer viewer) {
        dsl.insertInto(VIEWER)
                .set(TWITCH_ID, viewer.twitchId())
                .set(LOGIN, viewer.login())
                .set(DISPLAY_NAME, viewer.displayName())
                .set(PROFILE_IMAGE_URL, viewer.profileImageUrl())
                .onConflict(TWITCH_ID)
                .doUpdate()
                .set(LOGIN, DSL.field("EXCLUDED.login", String.class))
                .set(DISPLAY_NAME, DSL.field("EXCLUDED.display_name", String.class))
                .set(PROFILE_IMAGE_URL, DSL.field("EXCLUDED.profile_image_url", String.class))
                .execute();
        return viewer;
    }
}
