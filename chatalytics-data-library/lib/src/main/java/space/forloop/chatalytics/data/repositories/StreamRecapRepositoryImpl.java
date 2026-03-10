package space.forloop.chatalytics.data.repositories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
@Service
public class StreamRecapRepositoryImpl implements StreamRecapRepository {

    private final DSLContext dsl;
    private final ObjectMapper mapper;

    private static final org.jooq.Table<?> TABLE = table(name("chat", "stream_recap"));

    public StreamRecapRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Optional<StreamRecap> findBySessionId(long sessionId) {
        return dsl.selectFrom(TABLE)
                .where(field("session_id").eq(sessionId))
                .fetchOptional()
                .map(this::toStreamRecap);
    }

    @Override
    @Transactional
    public void save(StreamRecap recap) {
        dsl.insertInto(TABLE)
                .set(field("session_id"), recap.sessionId())
                .set(field("start_time"), toLocalDateTime(recap.startTime()))
                .set(field("end_time"), toLocalDateTime(recap.endTime()))
                .set(field("total_messages"), recap.totalMessages())
                .set(field("total_chatters"), recap.totalChatters())
                .set(field("messages_per_minute"), recap.messagesPerMinute())
                .set(field("chatters_per_minute"), recap.chattersPerMinute())
                .set(field("peak_viewer_count"), recap.peakViewerCount())
                .set(field("avg_viewer_count"), recap.avgViewerCount())
                .set(field("min_viewer_count"), recap.minViewerCount())
                .set(field("new_chatter_count"), recap.newChatterCount())
                .set(field("returning_chatter_count"), recap.returningChatterCount())
                .set(field("chat_participation_rate"), recap.chatParticipationRate())
                .set(field("avg_message_length"), recap.messageAnalysis() != null ? recap.messageAnalysis().avgMessageLength() : null)
                .set(field("median_message_length"), recap.messageAnalysis() != null ? recap.messageAnalysis().medianMessageLength() : null)
                .set(field("command_count"), recap.messageAnalysis() != null ? recap.messageAnalysis().commandCount() : null)
                .set(field("short_message_ratio"), recap.messageAnalysis() != null ? recap.messageAnalysis().shortMessageRatio() : null)
                .set(field("caps_ratio"), recap.messageAnalysis() != null ? recap.messageAnalysis().capsRatio() : null)
                .set(field("question_ratio"), recap.messageAnalysis() != null ? recap.messageAnalysis().questionRatio() : null)
                .set(field("exclamation_ratio"), recap.messageAnalysis() != null ? recap.messageAnalysis().exclamationRatio() : null)
                .set(field("link_count"), recap.messageAnalysis() != null ? recap.messageAnalysis().linkCount() : null)
                .set(field("peak_moment_timestamp"), recap.peakMoment() != null ? toLocalDateTime(recap.peakMoment().timestamp()) : null)
                .set(field("peak_moment_messages"), recap.peakMoment() != null ? recap.peakMoment().messageCount() : null)
                .set(field("peak_moment_chatters"), recap.peakMoment() != null ? recap.peakMoment().uniqueChatters() : null)
                .set(field("ai_summary"), recap.aiSummary())
                .set(field("snapshots"), toJsonb(recap.snapshots()))
                .set(field("chat_activity"), toJsonb(recap.chatActivity()))
                .set(field("top_chatters"), toJsonb(recap.topChatters()))
                .set(field("top_words"), toJsonb(recap.topWords()))
                .set(field("game_segments"), toJsonb(recap.gameSegments()))
                .set(field("top_clips"), toJsonb(recap.topClips()))
                .set(field("hype_moments"), toJsonb(recap.hypeMoments()))
                .onConflict(field("session_id"))
                .doNothing()
                .execute();
    }

    @Override
    public List<Long> findSessionIdsWithoutRecap() {
        var sessionTable = table(name("chat", "session"));
        return dsl.select(field(name("chat", "session", "id"), Long.class))
                .from(sessionTable)
                .where(field(name("chat", "session", "end_time")).isNotNull())
                .andNotExists(
                        dsl.selectOne()
                                .from(TABLE)
                                .where(field(name("chat", "stream_recap", "session_id"))
                                        .eq(field(name("chat", "session", "id"))))
                )
                .orderBy(field(name("chat", "session", "end_time")).desc())
                .limit(10)
                .fetchInto(Long.class);
    }

    private StreamRecap toStreamRecap(Record r) {
        Instant startTime = toInstant(r.get("start_time", LocalDateTime.class));
        Instant endTime = toInstant(r.get("end_time", LocalDateTime.class));

        Double avgMsgLen = r.get("avg_message_length", Double.class);
        MessageAnalysis analysis = avgMsgLen != null ? new MessageAnalysis(
                avgMsgLen,
                r.get("median_message_length", Double.class),
                r.get("command_count", Long.class),
                r.get("short_message_ratio", Double.class),
                r.get("caps_ratio", Double.class),
                r.get("question_ratio", Double.class),
                r.get("exclamation_ratio", Double.class),
                r.get("link_count", Long.class)
        ) : null;

        LocalDateTime peakTs = r.get("peak_moment_timestamp", LocalDateTime.class);
        ChatMoment peakMoment = peakTs != null ? new ChatMoment(
                toInstant(peakTs),
                r.get("peak_moment_messages", Long.class),
                r.get("peak_moment_chatters", Long.class)
        ) : null;

        return new StreamRecap(
                r.get("session_id", Long.class),
                startTime,
                endTime,
                r.get("total_messages", Long.class),
                r.get("total_chatters", Long.class),
                fromJsonb(r.get("snapshots", JSONB.class), new TypeReference<>() {}),
                fromJsonb(r.get("chat_activity", JSONB.class), new TypeReference<>() {}),
                fromJsonb(r.get("top_chatters", JSONB.class), new TypeReference<>() {}),
                r.get("ai_summary", String.class),
                r.get("messages_per_minute", Double.class),
                r.get("chatters_per_minute", Double.class),
                r.get("peak_viewer_count", Integer.class),
                r.get("avg_viewer_count", Double.class),
                r.get("min_viewer_count", Integer.class),
                analysis,
                r.get("new_chatter_count", Long.class),
                r.get("returning_chatter_count", Long.class),
                fromJsonb(r.get("top_words", JSONB.class), new TypeReference<>() {}),
                fromJsonb(r.get("game_segments", JSONB.class), new TypeReference<>() {}),
                r.get("chat_participation_rate", Double.class),
                peakMoment,
                fromJsonb(r.get("top_clips", JSONB.class), new TypeReference<>() {}),
                fromJsonb(r.get("hype_moments", JSONB.class), new TypeReference<>() {})
        );
    }

    private JSONB toJsonb(Object obj) {
        try {
            return JSONB.valueOf(mapper.writeValueAsString(obj));
        } catch (Exception e) {
            log.error("Failed to serialize to JSONB: {}", e.getMessage());
            return JSONB.valueOf("[]");
        }
    }

    private <T> List<T> fromJsonb(JSONB jsonb, TypeReference<List<T>> typeRef) {
        if (jsonb == null) return List.of();
        try {
            return mapper.readValue(jsonb.data(), typeRef);
        } catch (Exception e) {
            log.error("Failed to deserialize JSONB: {}", e.getMessage());
            return List.of();
        }
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
