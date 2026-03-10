package space.forloop.chatalytics.data.repositories;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.ChannelAuthenticity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
public class ChannelAuthenticityRepositoryImpl implements ChannelAuthenticityRepository {

    private final DSLContext dsl;
    private final ObjectMapper mapper;

    private static final org.jooq.Table<?> TABLE = table(name("chat", "channel_authenticity"));

    public ChannelAuthenticityRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
        this.mapper = new ObjectMapper();
    }

    @Override
    public Optional<ChannelAuthenticity> findByChannelId(long channelId) {
        return dsl.selectFrom(TABLE)
                .where(field("channel_id").eq(channelId))
                .fetchOptional()
                .map(this::toChannelAuthenticity);
    }

    @Override
    @Transactional
    public void save(ChannelAuthenticity ca) {
        dsl.insertInto(TABLE)
                .set(field("channel_id"), ca.channelId())
                .set(field("avg_authenticity_score"), ca.avgAuthenticityScore())
                .set(field("min_authenticity_score"), ca.minAuthenticityScore())
                .set(field("max_authenticity_score"), ca.maxAuthenticityScore())
                .set(field("trend_direction"), ca.trendDirection())
                .set(field("sessions_analyzed"), ca.sessionsAnalyzed())
                .set(field("risk_level"), ca.riskLevel())
                .set(field("risk_factors"), toJsonb(ca.riskFactors()))
                .set(field("updated_at"), LocalDateTime.now(ZoneOffset.UTC))
                .onConflict(field("channel_id"))
                .doUpdate()
                .set(field("avg_authenticity_score"), ca.avgAuthenticityScore())
                .set(field("min_authenticity_score"), ca.minAuthenticityScore())
                .set(field("max_authenticity_score"), ca.maxAuthenticityScore())
                .set(field("trend_direction"), ca.trendDirection())
                .set(field("sessions_analyzed"), ca.sessionsAnalyzed())
                .set(field("risk_level"), ca.riskLevel())
                .set(field("risk_factors"), toJsonb(ca.riskFactors()))
                .set(field("updated_at"), LocalDateTime.now(ZoneOffset.UTC))
                .execute();
    }

    private ChannelAuthenticity toChannelAuthenticity(Record r) {
        return new ChannelAuthenticity(
                r.get("channel_id", Long.class),
                r.get("avg_authenticity_score", Double.class),
                r.get("min_authenticity_score", Integer.class),
                r.get("max_authenticity_score", Integer.class),
                r.get("trend_direction", String.class),
                r.get("sessions_analyzed", Integer.class),
                r.get("risk_level", String.class),
                fromJsonb(r.get("risk_factors", JSONB.class)),
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

    private List<String> fromJsonb(JSONB jsonb) {
        if (jsonb == null) return List.of();
        try {
            return mapper.readValue(jsonb.data(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize JSONB: {}", e.getMessage());
            return List.of();
        }
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
