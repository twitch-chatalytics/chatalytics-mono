package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChatActivityBucket;
import space.forloop.chatalytics.data.domain.ChatterProfile;
import space.forloop.chatalytics.data.domain.MessageAnalysis;
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
    public List<Message> findByAuthor(String author, Long channelId, Instant from, Instant to, Instant beforeTimestamp, Long beforeId, int limit) {

        List<Condition> conditions = new ArrayList<>();
        conditions.add(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()));
        conditions.add(MESSAGE.CHANNEL_ID.eq(channelId));

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
    public List<String> searchAuthors(String query, Long channelId) {

        return dsl.selectDistinct(MESSAGE.AUTHOR)
                .from(MESSAGE)
                .where(DSL.lower(MESSAGE.AUTHOR).contains(query.toLowerCase()))
                .and(MESSAGE.CHANNEL_ID.eq(channelId))
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
    public List<Message> findContext(Long channelId, Instant timestamp, int seconds) {

        Instant from = timestamp.minusSeconds(seconds);
        Instant to = timestamp.plusSeconds(seconds);

        return dsl.selectFrom(MESSAGE)
                .where(MESSAGE.CHANNEL_ID.eq(channelId))
                .and(MESSAGE.TIMESTAMP.between(from, to))
                .orderBy(MESSAGE.TIMESTAMP.asc())
                .limit(200)
                .fetchInto(Message.class);
    }

    @Override
    public Long countAllMessages(Long channelId) {

        return dsl.selectCount()
                .from(MESSAGE)
                .where(MESSAGE.CHANNEL_ID.eq(channelId))
                .fetchOneInto(long.class);
    }

    @Override
    public Long countDistinctAuthors(Long channelId) {

        return dsl.select(DSL.countDistinct(MESSAGE.AUTHOR))
                .from(MESSAGE)
                .where(MESSAGE.CHANNEL_ID.eq(channelId))
                .fetchOneInto(long.class);
    }

    @Override
    public List<TopChatter> topChatters(Long channelId, int limit) {

        return dsl.select(MESSAGE.AUTHOR, DSL.count().as("messageCount"))
                .from(MESSAGE)
                .where(MESSAGE.CHANNEL_ID.eq(channelId))
                .groupBy(MESSAGE.AUTHOR)
                .orderBy(DSL.count().desc())
                .limit(limit)
                .fetchInto(TopChatter.class);
    }

    @Override
    public Optional<Integer> peakHour(Long channelId) {

        var hourField = DSL.extract(MESSAGE.TIMESTAMP, org.jooq.DatePart.HOUR);

        return dsl.select(hourField)
                .from(MESSAGE)
                .where(MESSAGE.CHANNEL_ID.eq(channelId))
                .groupBy(hourField)
                .orderBy(DSL.count().desc())
                .limit(1)
                .fetchOptionalInto(Integer.class);
    }

    @Override
    public Optional<ChatterProfile> chatterProfile(String author, Long channelId) {

        var hourField = DSL.extract(MESSAGE.TIMESTAMP, org.jooq.DatePart.HOUR);

        var peakHourSubquery = DSL.field(
                DSL.select(hourField)
                        .from(MESSAGE)
                        .where(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()))
                        .and(MESSAGE.CHANNEL_ID.eq(channelId))
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
                .and(MESSAGE.CHANNEL_ID.eq(channelId))
                .fetchOne();

        if (result == null || result.get("totalMessages", Long.class) == 0L) {
            return Optional.empty();
        }

        long total = result.get("totalMessages", Long.class);
        long sessions = result.get("distinctSessions", Long.class);
        double avg = sessions > 0 ? (double) total / sessions : 0.0;

        var repeats = findRepeatedMessages(author, channelId);

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
    public List<RepeatedMessage> findRepeatedMessages(String author, Long channelId) {

        var normalized = DSL.lower(DSL.trim(MESSAGE.MESSAGE_TEXT));

        return dsl.select(DSL.min(DSL.trim(MESSAGE.MESSAGE_TEXT)).as("text"), DSL.count().as("count"))
                .from(MESSAGE)
                .where(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()))
                .and(MESSAGE.CHANNEL_ID.eq(channelId))
                .groupBy(normalized)
                .having(DSL.count().ge(3))
                .orderBy(DSL.count().desc())
                .limit(10)
                .fetchInto(RepeatedMessage.class);
    }

    @Override
    public List<Message> findSampleByAuthor(String author, Long channelId, int limit) {

        return dsl.selectFrom(MESSAGE)
                .where(DSL.lower(MESSAGE.AUTHOR).eq(author.toLowerCase()))
                .and(MESSAGE.CHANNEL_ID.eq(channelId))
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

        // Use DSL.inline to embed the value directly so GROUP BY expression matches SELECT
        var bucketExpr = DSL.field(
                "to_timestamp(floor(extract(epoch from {0}) / {1}) * {1})",
                Instant.class,
                MESSAGE.TIMESTAMP,
                DSL.inline(bucketMinutes * 60)
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
        if (messages.isEmpty()) return;

        // Multi-row INSERT for maximum throughput (single SQL statement vs N round-trips)
        var insert = dsl.insertInto(MESSAGE,
                MESSAGE.CHANNEL_ID, MESSAGE.MESSAGE_TEXT, MESSAGE.TIMESTAMP,
                MESSAGE.SESSION_ID, MESSAGE.AUTHOR);

        for (Message dto : messages) {
            insert = insert.values(
                    dto.getChannelId(), dto.getMessageText(), dto.getTimestamp(),
                    dto.getSessionId(), dto.getAuthor());
        }

        insert.execute();
    }

    @Override
    public MessageAnalysis messageAnalysisBySessionId(Long sessionId) {
        var result = dsl.select(
                DSL.avg(DSL.length(MESSAGE.MESSAGE_TEXT)).as("avgLen"),
                DSL.field("PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY length(message_text))", Double.class).as("medianLen"),
                DSL.count().filterWhere(MESSAGE.MESSAGE_TEXT.startsWith("!")).as("commandCount"),
                DSL.count().filterWhere(DSL.length(MESSAGE.MESSAGE_TEXT).le(15)).cast(Double.class)
                        .div(DSL.nullif(DSL.count().cast(Double.class), 0.0)).as("shortRatio"),
                DSL.count().filterWhere(DSL.condition("message_text ~ '[A-Z]{3,}'")).cast(Double.class)
                        .div(DSL.nullif(DSL.count().cast(Double.class), 0.0)).as("capsRatio"),
                DSL.count().filterWhere(MESSAGE.MESSAGE_TEXT.contains("?")).cast(Double.class)
                        .div(DSL.nullif(DSL.count().cast(Double.class), 0.0)).as("questionRatio"),
                DSL.count().filterWhere(MESSAGE.MESSAGE_TEXT.endsWith("!")).cast(Double.class)
                        .div(DSL.nullif(DSL.count().cast(Double.class), 0.0)).as("exclamationRatio"),
                DSL.count().filterWhere(DSL.condition("message_text ~ 'https?://'")).as("linkCount")
        )
        .from(MESSAGE)
        .where(MESSAGE.SESSION_ID.eq(sessionId))
        .fetchOne();

        if (result == null) {
            return new MessageAnalysis(0, 0, 0, 0, 0, 0, 0, 0);
        }

        return new MessageAnalysis(
                result.get("avgLen", Double.class) != null ? result.get("avgLen", Double.class) : 0.0,
                result.get("medianLen", Double.class) != null ? result.get("medianLen", Double.class) : 0.0,
                result.get("commandCount", Long.class) != null ? result.get("commandCount", Long.class) : 0L,
                result.get("shortRatio", Double.class) != null ? result.get("shortRatio", Double.class) : 0.0,
                result.get("capsRatio", Double.class) != null ? result.get("capsRatio", Double.class) : 0.0,
                result.get("questionRatio", Double.class) != null ? result.get("questionRatio", Double.class) : 0.0,
                result.get("exclamationRatio", Double.class) != null ? result.get("exclamationRatio", Double.class) : 0.0,
                result.get("linkCount", Long.class) != null ? result.get("linkCount", Long.class) : 0L
        );
    }

    @Override
    public long newChatterCountBySessionId(Long sessionId) {
        return dsl.select(DSL.countDistinct(MESSAGE.AUTHOR))
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .andNotExists(
                        dsl.selectOne()
                                .from(MESSAGE.as("earlier"))
                                .where(DSL.field(DSL.name("earlier", "author"), String.class).eq(MESSAGE.AUTHOR))
                                .and(DSL.field(DSL.name("earlier", "session_id"), Long.class).ne(sessionId))
                                .and(DSL.field(DSL.name("earlier", "timestamp"), Instant.class).lt(
                                        dsl.select(DSL.min(MESSAGE.TIMESTAMP))
                                                .from(MESSAGE)
                                                .where(MESSAGE.SESSION_ID.eq(sessionId))
                                                .and(MESSAGE.AUTHOR.eq(DSL.field(DSL.name("earlier", "author"), String.class)))
                                ))
                )
                .fetchOneInto(long.class);
    }

    @Override
    public long countMessagesBySessionIdAndTimeRange(Long sessionId, Instant from, Instant to) {
        return dsl.selectCount()
                .from(MESSAGE)
                .where(MESSAGE.SESSION_ID.eq(sessionId))
                .and(MESSAGE.TIMESTAMP.ge(from))
                .and(MESSAGE.TIMESTAMP.lt(to))
                .fetchOneInto(long.class);
    }

    @Override
    public double avgMessagesPerSession(Long channelId) {
        Double result = dsl.select(DSL.avg(DSL.field("cnt", Long.class)))
                .from(
                        dsl.select(DSL.count().as("cnt"))
                                .from(MESSAGE)
                                .where(MESSAGE.CHANNEL_ID.eq(channelId))
                                .groupBy(MESSAGE.SESSION_ID)
                )
                .fetchOneInto(Double.class);
        return result != null ? result : 0.0;
    }

    @Override
    public double avgChattersPerSession(Long channelId) {
        Double result = dsl.select(DSL.avg(DSL.field("cnt", Long.class)))
                .from(
                        dsl.select(DSL.countDistinct(MESSAGE.AUTHOR).as("cnt"))
                                .from(MESSAGE)
                                .where(MESSAGE.CHANNEL_ID.eq(channelId))
                                .groupBy(MESSAGE.SESSION_ID)
                )
                .fetchOneInto(Double.class);
        return result != null ? result : 0.0;
    }
}
