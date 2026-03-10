package space.forloop.chatalytics.data.repositories;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.AlertEvent;
import space.forloop.chatalytics.data.domain.AlertRule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.jooq.impl.DSL.*;

@Slf4j
public class AlertRepositoryImpl implements AlertRepository {

    private final DSLContext dsl;

    private static final org.jooq.Table<?> RULE_TABLE = table(name("chat", "alert_rule"));
    private static final org.jooq.Table<?> EVENT_TABLE = table(name("chat", "alert_event"));

    public AlertRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<AlertRule> findRulesByChannelId(long channelId) {
        return dsl.selectFrom(RULE_TABLE)
                .where(field("channel_id").eq(channelId))
                .orderBy(field("created_at").desc())
                .fetch()
                .map(this::toAlertRule);
    }

    @Override
    public List<AlertRule> findAllEnabledRules() {
        return dsl.selectFrom(RULE_TABLE)
                .where(field("enabled").eq(true))
                .fetch()
                .map(this::toAlertRule);
    }

    @Override
    @Transactional
    public AlertRule saveRule(AlertRule rule) {
        Record record = dsl.insertInto(RULE_TABLE)
                .set(field("channel_id"), rule.channelId())
                .set(field("alert_type"), rule.alertType())
                .set(field("threshold_value"), rule.thresholdValue())
                .set(field("enabled"), rule.enabled())
                .set(field("created_at"), LocalDateTime.now(ZoneOffset.UTC))
                .returning()
                .fetchOne();

        return record != null ? toAlertRule(record) : rule;
    }

    @Override
    @Transactional
    public void deleteRule(long id) {
        dsl.deleteFrom(RULE_TABLE)
                .where(field("id").eq(id))
                .execute();
    }

    @Override
    public List<AlertEvent> findEventsByChannelId(long channelId, int limit) {
        return dsl.selectFrom(EVENT_TABLE)
                .where(field("channel_id").eq(channelId))
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetch()
                .map(this::toAlertEvent);
    }

    @Override
    public List<AlertEvent> findRecentEvents(int limit) {
        return dsl.selectFrom(EVENT_TABLE)
                .orderBy(field("created_at").desc())
                .limit(limit)
                .fetch()
                .map(this::toAlertEvent);
    }

    @Override
    @Transactional
    public void saveEvent(AlertEvent event) {
        dsl.insertInto(EVENT_TABLE)
                .set(field("alert_rule_id"), event.alertRuleId())
                .set(field("channel_id"), event.channelId())
                .set(field("alert_type"), event.alertType())
                .set(field("message"), event.message())
                .set(field("severity"), event.severity())
                .set(field("acknowledged"), event.acknowledged())
                .set(field("created_at"), LocalDateTime.now(ZoneOffset.UTC))
                .execute();
    }

    @Override
    @Transactional
    public void acknowledgeEvent(long eventId) {
        dsl.update(EVENT_TABLE)
                .set(field("acknowledged"), true)
                .where(field("id").eq(eventId))
                .execute();
    }

    private AlertRule toAlertRule(Record r) {
        return new AlertRule(
                r.get("id", Long.class),
                r.get("channel_id", Long.class),
                r.get("alert_type", String.class),
                r.get("threshold_value", Double.class),
                r.get("enabled", Boolean.class),
                toInstant(r.get("created_at", LocalDateTime.class))
        );
    }

    private AlertEvent toAlertEvent(Record r) {
        return new AlertEvent(
                r.get("id", Long.class),
                r.get("alert_rule_id", Long.class),
                r.get("channel_id", Long.class),
                r.get("alert_type", String.class),
                r.get("message", String.class),
                r.get("severity", String.class),
                r.get("acknowledged", Boolean.class),
                toInstant(r.get("created_at", LocalDateTime.class))
        );
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
