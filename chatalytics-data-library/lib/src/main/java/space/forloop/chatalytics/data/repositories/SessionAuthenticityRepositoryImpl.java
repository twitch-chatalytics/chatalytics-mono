package space.forloop.chatalytics.data.repositories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.AuthenticityTrendPoint;
import space.forloop.chatalytics.data.domain.SessionAuthenticity;
import space.forloop.chatalytics.data.domain.SessionAuthenticity.SuspiciousFlag;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
public class SessionAuthenticityRepositoryImpl implements SessionAuthenticityRepository {

    private final DSLContext dsl;
    private final ObjectMapper mapper;

    private static final org.jooq.Table<?> TABLE = table(name("chat", "session_authenticity"));
    private static final org.jooq.Table<?> SESSION_TABLE = table(name("chat", "session"));
    private static final org.jooq.Table<?> RECAP_TABLE = table(name("chat", "stream_recap"));

    public SessionAuthenticityRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
        this.mapper = new ObjectMapper();
    }

    @Override
    public Optional<SessionAuthenticity> findBySessionId(long sessionId) {
        return dsl.selectFrom(TABLE)
                .where(field("session_id").eq(sessionId))
                .fetchOptional()
                .map(this::toSessionAuthenticity);
    }

    @Override
    @Transactional
    public void save(SessionAuthenticity a) {
        dsl.insertInto(TABLE)
                .set(field("session_id"), a.sessionId())
                .set(field("channel_id"), a.channelId())
                .set(field("authenticity_score"), a.authenticityScore())
                .set(field("confidence_level"), a.confidenceLevel())
                .set(field("chat_viewer_ratio"), a.chatViewerRatio())
                .set(field("expected_chat_ratio"), a.expectedChatRatio())
                .set(field("chat_ratio_deviation"), a.chatRatioDeviation())
                .set(field("vocabulary_diversity"), a.vocabularyDiversity())
                .set(field("emote_only_ratio"), a.emoteOnlyRatio())
                .set(field("repetitive_message_ratio"), a.repetitiveMessageRatio())
                .set(field("single_message_chatter_ratio"), a.singleMessageChatterRatio())
                .set(field("timing_uniformity_score"), a.timingUniformityScore())
                .set(field("organic_flow_score"), a.organicFlowScore())
                .set(field("conversation_depth_score"), a.conversationDepthScore())
                .set(field("viewer_chat_correlation"), a.viewerChatCorrelation())
                .set(field("suspicious_pattern_flags"), toJsonb(a.suspiciousPatternFlags()))
                .set(field("algorithm_version"), a.algorithmVersion())
                .set(field("computed_at"), toLocalDateTime(a.computedAt()))
                .onConflict(field("session_id"))
                .doNothing()
                .execute();
    }

    @Override
    public List<Long> findSessionIdsWithoutAuthenticity() {
        return dsl.select(field(name("chat", "stream_recap", "session_id"), Long.class))
                .from(RECAP_TABLE)
                .where(
                        notExists(
                                dsl.selectOne()
                                        .from(TABLE)
                                        .where(field(name("chat", "session_authenticity", "session_id"))
                                                .eq(field(name("chat", "stream_recap", "session_id"))))
                        )
                )
                .orderBy(field(name("chat", "stream_recap", "session_id")).desc())
                .limit(20)
                .fetchInto(Long.class);
    }

    @Override
    public List<SessionAuthenticity> findByChannelId(long channelId, int limit, int offset) {
        return dsl.select(TABLE.asterisk(), field(name("chat", "session", "start_time")))
                .from(TABLE)
                .leftJoin(SESSION_TABLE)
                .on(field(name("chat", "session_authenticity", "session_id"))
                        .eq(field(name("chat", "session", "id"))))
                .where(field(name("chat", "session_authenticity", "channel_id")).eq(channelId))
                .orderBy(field(name("chat", "session", "start_time")).desc())
                .limit(limit)
                .offset(offset)
                .fetch()
                .map(this::toSessionAuthenticityWithStartTime);
    }

    @Override
    public List<AuthenticityTrendPoint> findTrendByChannelId(long channelId, int limit) {
        var recapTable = table(name("chat", "stream_recap"));
        return dsl.select(
                        field(name("chat", "session_authenticity", "computed_at")),
                        field(name("chat", "session_authenticity", "authenticity_score")),
                        field(name("chat", "stream_recap", "peak_viewer_count"))
                )
                .from(TABLE)
                .leftJoin(recapTable)
                .on(field(name("chat", "session_authenticity", "session_id"))
                        .eq(field(name("chat", "stream_recap", "session_id"))))
                .where(field(name("chat", "session_authenticity", "channel_id")).eq(channelId))
                .orderBy(field(name("chat", "session_authenticity", "computed_at")).asc())
                .limit(limit)
                .fetch()
                .map(r -> new AuthenticityTrendPoint(
                        toInstant(r.get(0, LocalDateTime.class)),
                        r.get(1, Integer.class),
                        r.get(2, Integer.class)
                ));
    }

    @Override
    public List<Long> findChannelIdsWithoutChannelRollup() {
        var channelTable = table(name("chat", "channel_authenticity"));
        return dsl.selectDistinct(field(name("chat", "session_authenticity", "channel_id"), Long.class))
                .from(TABLE)
                .where(
                        notExists(
                                dsl.selectOne()
                                        .from(channelTable)
                                        .where(field(name("chat", "channel_authenticity", "channel_id"))
                                                .eq(field(name("chat", "session_authenticity", "channel_id"))))
                        )
                )
                .fetchInto(Long.class);
    }

    private SessionAuthenticity toSessionAuthenticity(Record r) {
        return new SessionAuthenticity(
                r.get("session_id", Long.class),
                r.get("channel_id", Long.class),
                r.get("authenticity_score", Integer.class),
                r.get("confidence_level", String.class),
                r.get("chat_viewer_ratio", Double.class),
                r.get("expected_chat_ratio", Double.class),
                r.get("chat_ratio_deviation", Double.class),
                r.get("vocabulary_diversity", Double.class),
                r.get("emote_only_ratio", Double.class),
                r.get("repetitive_message_ratio", Double.class),
                r.get("single_message_chatter_ratio", Double.class),
                r.get("timing_uniformity_score", Double.class),
                r.get("organic_flow_score", Double.class),
                r.get("conversation_depth_score", Double.class),
                r.get("viewer_chat_correlation", Double.class),
                fromJsonb(r.get("suspicious_pattern_flags", JSONB.class)),
                r.get("algorithm_version", Integer.class),
                toInstant(r.get("computed_at", LocalDateTime.class)),
                null
        );
    }

    private SessionAuthenticity toSessionAuthenticityWithStartTime(Record r) {
        return new SessionAuthenticity(
                r.get("session_id", Long.class),
                r.get("channel_id", Long.class),
                r.get("authenticity_score", Integer.class),
                r.get("confidence_level", String.class),
                r.get("chat_viewer_ratio", Double.class),
                r.get("expected_chat_ratio", Double.class),
                r.get("chat_ratio_deviation", Double.class),
                r.get("vocabulary_diversity", Double.class),
                r.get("emote_only_ratio", Double.class),
                r.get("repetitive_message_ratio", Double.class),
                r.get("single_message_chatter_ratio", Double.class),
                r.get("timing_uniformity_score", Double.class),
                r.get("organic_flow_score", Double.class),
                r.get("conversation_depth_score", Double.class),
                r.get("viewer_chat_correlation", Double.class),
                fromJsonb(r.get("suspicious_pattern_flags", JSONB.class)),
                r.get("algorithm_version", Integer.class),
                toInstant(r.get("computed_at", LocalDateTime.class)),
                toInstant(r.get("start_time", LocalDateTime.class))
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

    private List<SuspiciousFlag> fromJsonb(JSONB jsonb) {
        if (jsonb == null) return List.of();
        try {
            return mapper.readValue(jsonb.data(), new TypeReference<>() {});
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
