package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChatActivityBucket;
import space.forloop.chatalytics.data.domain.ChatterProfile;
import space.forloop.chatalytics.data.domain.RepeatedMessage;
import space.forloop.chatalytics.data.domain.TopChatter;
import space.forloop.chatalytics.data.generated.tables.pojos.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static space.forloop.chatalytics.data.generated.tables.Message.MESSAGE;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRepositoryImpl implements MessageRepository {

    private final DSLContext dsl;

    @Override
    public Long countChattersBySessionId(Long sessionId) {

        return dsl.select(DSL.countDistinct(MESSAGE.AUTHOR))
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .fetchOneInto(long.class);
    }

    @Override
    public Long countMessagesBySessionId(Long sessionId) {

        return dsl.selectCount()
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .fetchOneInto(long.class);
    }

    @Override
    public Long countMentionsBySessionId(Long sessionId, String channelName) {

        return dsl.selectCount()
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .and(DSL.lower(MESSAGE.MESSAGE_TEXT).contains(DSL.lower(channelName)))
                .fetchOneInto(long.class);
    }

    @Override
    public Optional<TopChatter> topChatterByMessageCount(Long sessionId, List<String> ignoredAuthors) {

        return dsl.select(MESSAGE.AUTHOR, DSL.count())
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .and(MESSAGE.AUTHOR.notIn(ignoredAuthors))
                .groupBy(MESSAGE.AUTHOR)
                .orderBy(DSL.count().desc())
                .limit(1)
                .fetchOptionalInto(TopChatter.class);
    }

    @Override
    public List<Message> findByAuthor(String author, Long twitchId, Instant from, Instant to, Instant beforeTimestamp, Long beforeId, int limit) {

        List<Condition> conditions = new ArrayList<>();
        conditions.add(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()));
        conditions.add(MESSAGE.TWITCH_ID.eq(twitchId));

        if (from != null) {
            conditions.add(MESSAGE.TIMESTAMP.ge(from));
        }
        if (to != null) {
            conditions.add(MESSAGE.TIMESTAMP.le(to));
        }
        if (beforeTimestamp != null && beforeId != null) {
            conditions.add(
                    DSL.row(MESSAGE.TIMESTAMP, MESSAGE.ID).lessThan(DSL.row(DSL.val(beforeTimestamp), DSL.val(beforeId)))
            );
        }

        return dsl.selectFrom(MESSAGE)
                .where(conditions)
                .orderBy(MESSAGE.TIMESTAMP.desc(), MESSAGE.ID.desc())
                .limit(limit)
                .fetchInto(Message.class);
    }

    @Override
    public List<String> searchAuthors(String query, Long twitchId) {

        return dsl.selectDistinct(MESSAGE.AUTHOR)
                .from(MESSAGE)
                .where(DSL.lower(MESSAGE.AUTHOR).contains(query.toLowerCase()))
                .and(MESSAGE.TWITCH_ID.eq(twitchId))
                .orderBy(MESSAGE.AUTHOR)
                .limit(10)
                .fetchInto(String.class);
    }

    @Override
    public Optional<Message> findById(Long id) {

        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.ID.eq(id))
                .fetchOptionalInto(Message.class);
    }

    @Override
    public List<Message> findContext(Long twitchId, Instant timestamp, int seconds) {

        Instant from = timestamp.minusSeconds(seconds);
        Instant to = timestamp.plusSeconds(seconds);

        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.TWITCH_ID.eq(twitchId))
                .and(MESSAGE.TIMESTAMP.between(from, to))
                .orderBy(MESSAGE.TIMESTAMP.asc())
                .limit(200)
                .fetchInto(Message.class);
    }

    @Override
    public Long countAllMessages(Long twitchId) {

        return dsl.selectCount()
                .from(MESSAGE)
                .where(MESSAGE.TWITCH_ID.eq(twitchId))
                .fetchOneInto(long.class);
    }

    @Override
    public Long countDistinctAuthors(Long twitchId) {

        return dsl.select(DSL.countDistinct(MESSAGE.AUTHOR))
                .from(MESSAGE)
                .where(MESSAGE.TWITCH_ID.eq(twitchId))
                .fetchOneInto(long.class);
    }

    @Override
    public List<TopChatter> topChatters(Long twitchId, int limit) {

        return dsl.select(MESSAGE.AUTHOR, DSL.count().as("messageCount"))
                .from(MESSAGE)
                .where(MESSAGE.TWITCH_ID.eq(twitchId))
                .groupBy(MESSAGE.AUTHOR)
                .orderBy(DSL.count().desc())
                .limit(limit)
                .fetchInto(TopChatter.class);
    }

    @Override
    public Optional<Integer> peakHour(Long twitchId) {

        var hourField = DSL.extract(MESSAGE.TIMESTAMP, org.jooq.DatePart.HOUR);

        return dsl.select(hourField)
                .from(MESSAGE)
                .where(MESSAGE.TWITCH_ID.eq(twitchId))
                .groupBy(hourField)
                .orderBy(DSL.count().desc())
                .limit(1)
                .fetchOptionalInto(Integer.class);
    }

    @Override
    public Optional<ChatterProfile> chatterProfile(String author, Long twitchId) {

        var hourField = DSL.extract(MESSAGE.TIMESTAMP, org.jooq.DatePart.HOUR);

        var peakHourSubquery = DSL.field(
                DSL.select(hourField)
                        .from(MESSAGE)
                        .where(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()))
                        .and(MESSAGE.TWITCH_ID.eq(twitchId))
                        .groupBy(hourField)
                        .orderBy(DSL.count().desc())
                        .limit(1)
        );

        var result = dsl.select(
                        DSL.count().as("totalMessages"),
                        DSL.min(MESSAGE.TIMESTAMP).as("firstSeen"),
                        DSL.max(MESSAGE.TIMESTAMP).as("lastSeen"),
                        DSL.countDistinct(MESSAGE.SESSION_ID).as("distinctSessions"),
                        peakHourSubquery.as("peakHour")
                )
                .from(MESSAGE)
                .where(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()))
                .and(MESSAGE.TWITCH_ID.eq(twitchId))
                .fetchOne();

        if (result == null || result.get("totalMessages", Long.class) == 0L) {
            return Optional.empty();
        }

        long total = result.get("totalMessages", Long.class);
        long sessions = result.get("distinctSessions", Long.class);
        double avg = sessions > 0 ? (double) total / sessions : 0.0;

        var repeats = findRepeatedMessages(author, twitchId);

        return Optional.of(new ChatterProfile(
                author,
                total,
                result.get("firstSeen", Instant.class),
                result.get("lastSeen", Instant.class),
                sessions,
                result.get("peakHour", Integer.class),
                avg,
                repeats
        ));
    }

    @Override
    public List<RepeatedMessage> findRepeatedMessages(String author, Long twitchId) {

        var normalized = DSL.lower(DSL.trim(MESSAGE.MESSAGE_TEXT));

        return dsl.select(DSL.min(DSL.trim(MESSAGE.MESSAGE_TEXT)).as("text"), DSL.count().as("count"))
                .from(MESSAGE)
                .where(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()))
                .and(MESSAGE.TWITCH_ID.eq(twitchId))
                .groupBy(normalized)
                .having(DSL.count().ge(3))
                .orderBy(DSL.count().desc())
                .limit(10)
                .fetchInto(RepeatedMessage.class);
    }

    @Override
    public List<Message> findSampleByAuthor(String author, Long twitchId, int limit) {

        return dsl.selectFrom(MESSAGE)
                .where(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()))
                .and(MESSAGE.TWITCH_ID.eq(twitchId))
                .orderBy(DSL.rand())
                .limit(limit)
                .fetchInto(Message.class);
    }

    @Override
    public List<TopChatter> topChattersBySessionId(Long sessionId, int limit) {

        return dsl.select(MESSAGE.AUTHOR, DSL.count().as("messageCount"))
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .groupBy(MESSAGE.AUTHOR)
                .orderBy(DSL.count().desc())
                .limit(limit)
                .fetchInto(TopChatter.class);
    }

    @Override
    public List<ChatActivityBucket> chatActivityBySessionId(Long sessionId, int bucketMinutes) {

        var epoch = DSL.epoch(MESSAGE.TIMESTAMP);
        var bucketSeconds = DSL.val(bucketMinutes * 60L);
        var bucketField = DSL.epoch(
                DSL.field("to_timestamp(floor({0} / {1}) * {1})", Object.class, epoch, bucketSeconds)
        );

        // Use raw SQL for time bucketing since jOOQ's timestamp arithmetic is verbose
        var bucketExpr = DSL.field(
                "to_timestamp(floor(extract(epoch from {0}) / {1}) * {1})",
                Instant.class,
                MESSAGE.TIMESTAMP,
                DSL.val(bucketMinutes * 60)
        );

        return dsl.select(
                        bucketExpr.as("bucketStart"),
                        DSL.count().as("messageCount"),
                        DSL.countDistinct(MESSAGE.AUTHOR).as("uniqueChatters")
                )
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .groupBy(bucketExpr)
                .orderBy(bucketExpr.asc())
                .fetchInto(ChatActivityBucket.class);
    }

    @Override
    public List<Message> findSampleBySessionId(Long sessionId, int limit) {

        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .orderBy(DSL.rand())
                .limit(limit)
                .fetchInto(Message.class);
    }

    @Override
    public void batchWrite(List<Message> messages) {

        dsl.batched(ctx -> messages.forEach(dto ->
                ctx.dsl().insertInto(MESSAGE)
                        .set(ctx.dsl().newRecord(MESSAGE, dto))
                        .onConflict(MESSAGE.ID)
                        .doUpdate()
                        .set(ctx.dsl().newRecord(MESSAGE, dto))
                        .execute()
        ));
    }
}
