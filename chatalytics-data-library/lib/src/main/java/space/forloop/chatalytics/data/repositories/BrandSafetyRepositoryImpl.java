package space.forloop.chatalytics.data.repositories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.ChannelBrandSafety;
import space.forloop.chatalytics.data.domain.ChannelBrandSafety.TopicCount;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
public class BrandSafetyRepositoryImpl implements BrandSafetyRepository {

    private final DSLContext dsl;
    private final ObjectMapper mapper;

    private static final org.jooq.Table<?> TABLE = table(name("twitch", "channel_brand_safety"));

    public BrandSafetyRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
        this.mapper = new ObjectMapper();
    }

    @Override
    public Optional<ChannelBrandSafety> findByTwitchId(long twitchId) {
        return dsl.selectFrom(TABLE)
                .where(field("twitch_id").eq(twitchId))
                .fetchOptional()
                .map(this::toChannelBrandSafety);
    }

    @Override
    @Transactional
    public void save(ChannelBrandSafety bs) {
        dsl.insertInto(TABLE)
                .set(field("twitch_id"), bs.twitchId())
                .set(field("brand_safety_score"), bs.brandSafetyScore())
                .set(field("toxicity_rate"), bs.toxicityRate())
                .set(field("positive_rate"), bs.positiveRate())
                .set(field("negative_rate"), bs.negativeRate())
                .set(field("neutral_rate"), bs.neutralRate())
                .set(field("emote_spam_rate"), bs.emoteSpamRate())
                .set(field("conversation_ratio"), bs.conversationRatio())
                .set(field("top_topics"), toJsonb(bs.topTopics()))
                .set(field("language_distribution"), toJsonb(bs.languageDistribution()))
                .set(field("sessions_analyzed"), bs.sessionsAnalyzed())
                .set(field("updated_at"), LocalDateTime.now(ZoneOffset.UTC))
                .onConflict(field("twitch_id"))
                .doUpdate()
                .set(field("brand_safety_score"), bs.brandSafetyScore())
                .set(field("toxicity_rate"), bs.toxicityRate())
                .set(field("positive_rate"), bs.positiveRate())
                .set(field("negative_rate"), bs.negativeRate())
                .set(field("neutral_rate"), bs.neutralRate())
                .set(field("emote_spam_rate"), bs.emoteSpamRate())
                .set(field("conversation_ratio"), bs.conversationRatio())
                .set(field("top_topics"), toJsonb(bs.topTopics()))
                .set(field("language_distribution"), toJsonb(bs.languageDistribution()))
                .set(field("sessions_analyzed"), bs.sessionsAnalyzed())
                .set(field("updated_at"), LocalDateTime.now(ZoneOffset.UTC))
                .execute();
    }

    private ChannelBrandSafety toChannelBrandSafety(Record r) {
        return new ChannelBrandSafety(
                r.get("twitch_id", Long.class),
                r.get("brand_safety_score", Integer.class),
                r.get("toxicity_rate", Double.class),
                r.get("positive_rate", Double.class),
                r.get("negative_rate", Double.class),
                r.get("neutral_rate", Double.class),
                r.get("emote_spam_rate", Double.class),
                r.get("conversation_ratio", Double.class),
                fromJsonbTopics(r.get("top_topics", JSONB.class)),
                fromJsonbMap(r.get("language_distribution", JSONB.class)),
                r.get("sessions_analyzed", Integer.class),
                toInstant(r.get("updated_at", LocalDateTime.class))
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

    private List<TopicCount> fromJsonbTopics(JSONB jsonb) {
        if (jsonb == null) return List.of();
        try {
            return mapper.readValue(jsonb.data(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize JSONB topics: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Double> fromJsonbMap(JSONB jsonb) {
        if (jsonb == null) return Map.of();
        try {
            return mapper.readValue(jsonb.data(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize JSONB map: {}", e.getMessage());
            return Map.of();
        }
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
