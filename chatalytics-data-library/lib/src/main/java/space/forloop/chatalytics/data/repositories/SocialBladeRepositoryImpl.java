package space.forloop.chatalytics.data.repositories;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import space.forloop.chatalytics.data.domain.SocialBladeChannel;
import space.forloop.chatalytics.data.domain.SocialBladeDailyPoint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
public class SocialBladeRepositoryImpl implements SocialBladeRepository {

    private final DSLContext dsl;

    private static final org.jooq.Table<?> CHANNEL_TABLE = table(name("twitch", "socialblade_channel"));
    private static final org.jooq.Table<?> DAILY_TABLE = table(name("twitch", "socialblade_daily"));

    public SocialBladeRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<SocialBladeChannel> findByTwitchId(long twitchId) {
        return dsl.selectFrom(CHANNEL_TABLE)
                .where(field("twitch_id").eq(twitchId))
                .fetchOptional()
                .map(this::toSocialBladeChannel);
    }

    @Override
    public void save(SocialBladeChannel ch) {
        dsl.insertInto(CHANNEL_TABLE)
                .set(field("twitch_id"), ch.twitchId())
                .set(field("username"), ch.username())
                .set(field("display_name"), ch.displayName())
                .set(field("followers"), ch.followers())
                .set(field("views"), ch.views())
                .set(field("grade"), ch.grade())
                .set(field("rank"), ch.rank())
                .set(field("follower_rank"), ch.followerRank())
                .set(field("followers_gained_30d"), ch.followersGained30d())
                .set(field("followers_gained_90d"), ch.followersGained90d())
                .set(field("followers_gained_180d"), ch.followersGained180d())
                .set(field("views_gained_30d"), ch.viewsGained30d())
                .set(field("views_gained_90d"), ch.viewsGained90d())
                .set(field("views_gained_180d"), ch.viewsGained180d())
                .set(field("youtube_url"), ch.youtubeUrl())
                .set(field("twitter_url"), ch.twitterUrl())
                .set(field("instagram_url"), ch.instagramUrl())
                .set(field("discord_url"), ch.discordUrl())
                .set(field("tiktok_url"), ch.tiktokUrl())
                .set(field("fetched_at"), LocalDateTime.now(ZoneOffset.UTC))
                .set(field("updated_at"), LocalDateTime.now(ZoneOffset.UTC))
                .onConflict(field("twitch_id"))
                .doUpdate()
                .set(field("username"), ch.username())
                .set(field("display_name"), ch.displayName())
                .set(field("followers"), ch.followers())
                .set(field("views"), ch.views())
                .set(field("grade"), ch.grade())
                .set(field("rank"), ch.rank())
                .set(field("follower_rank"), ch.followerRank())
                .set(field("followers_gained_30d"), ch.followersGained30d())
                .set(field("followers_gained_90d"), ch.followersGained90d())
                .set(field("followers_gained_180d"), ch.followersGained180d())
                .set(field("views_gained_30d"), ch.viewsGained30d())
                .set(field("views_gained_90d"), ch.viewsGained90d())
                .set(field("views_gained_180d"), ch.viewsGained180d())
                .set(field("youtube_url"), ch.youtubeUrl())
                .set(field("twitter_url"), ch.twitterUrl())
                .set(field("instagram_url"), ch.instagramUrl())
                .set(field("discord_url"), ch.discordUrl())
                .set(field("tiktok_url"), ch.tiktokUrl())
                .set(field("updated_at"), LocalDateTime.now(ZoneOffset.UTC))
                .execute();
    }

    @Override
    public void saveDailyPoints(long twitchId, List<SocialBladeDailyPoint> points) {
        if (points.isEmpty()) return;

        var batch = dsl.batch(
                dsl.insertInto(DAILY_TABLE)
                        .set(field("twitch_id"), (Object) null)
                        .set(field("date"), (Object) null)
                        .set(field("followers"), (Object) null)
                        .set(field("views"), (Object) null)
                        .set(field("follower_change"), (Object) null)
                        .set(field("view_change"), (Object) null)
                        .onConflict(field("twitch_id"), field("date"))
                        .doNothing()
        );

        for (SocialBladeDailyPoint p : points) {
            batch.bind(p.twitchId(), p.date(), p.followers(), p.views(), p.followerChange(), p.viewChange());
        }

        batch.execute();
    }

    @Override
    public List<SocialBladeDailyPoint> findDailyByTwitchId(long twitchId, int limit) {
        return dsl.selectFrom(DAILY_TABLE)
                .where(field("twitch_id").eq(twitchId))
                .orderBy(field("date").desc())
                .limit(limit)
                .fetch()
                .map(this::toDailyPoint);
    }

    @Override
    public List<Long> findStaleChannelIds(int staleHours) {
        // Find channels that have session_authenticity data but either:
        // 1. No socialblade_channel row yet, or
        // 2. socialblade_channel.updated_at is older than staleHours
        var sessionTable = table(name("twitch", "session_authenticity"));
        var threshold = LocalDateTime.now(ZoneOffset.UTC).minusHours(staleHours);

        return dsl.selectDistinct(field(name("twitch", "session_authenticity", "twitch_id"), Long.class))
                .from(sessionTable)
                .where(notExists(
                        dsl.selectOne().from(CHANNEL_TABLE)
                                .where(field(name("twitch", "socialblade_channel", "twitch_id"))
                                        .eq(field(name("twitch", "session_authenticity", "twitch_id"))))
                                .and(field(name("twitch", "socialblade_channel", "updated_at")).greaterThan(threshold))
                ))
                .fetchInto(Long.class);
    }

    private SocialBladeChannel toSocialBladeChannel(Record r) {
        return new SocialBladeChannel(
                r.get("twitch_id", Long.class),
                r.get("username", String.class),
                r.get("display_name", String.class),
                r.get("followers", Long.class),
                r.get("views", Long.class),
                r.get("grade", String.class),
                r.get("rank", Integer.class),
                r.get("follower_rank", Integer.class),
                r.get("followers_gained_30d", Integer.class),
                r.get("followers_gained_90d", Integer.class),
                r.get("followers_gained_180d", Integer.class),
                r.get("views_gained_30d", Long.class),
                r.get("views_gained_90d", Long.class),
                r.get("views_gained_180d", Long.class),
                r.get("youtube_url", String.class),
                r.get("twitter_url", String.class),
                r.get("instagram_url", String.class),
                r.get("discord_url", String.class),
                r.get("tiktok_url", String.class),
                toInstant(r.get("fetched_at", LocalDateTime.class)),
                toInstant(r.get("updated_at", LocalDateTime.class))
        );
    }

    private SocialBladeDailyPoint toDailyPoint(Record r) {
        return new SocialBladeDailyPoint(
                r.get("twitch_id", Long.class),
                r.get("date", LocalDate.class),
                r.get("followers", Long.class),
                r.get("views", Long.class),
                r.get("follower_change", Integer.class),
                r.get("view_change", Long.class)
        );
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
