package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.*;
import space.forloop.chatalytics.data.repositories.AlertRepository;
import space.forloop.chatalytics.data.repositories.ChannelAuthenticityRepository;
import space.forloop.chatalytics.data.repositories.SocialBladeRepository;

import java.util.List;

import static org.jooq.impl.DSL.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final AlertRepository alertRepository;
    private final ChannelAuthenticityRepository channelAuthenticityRepository;
    private final SocialBladeRepository socialBladeRepository;
    private final DSLContext dsl;

    public void evaluateAlerts() {
        List<AlertRule> rules = alertRepository.findAllEnabledRules();
        if (rules.isEmpty()) return;

        log.info("Evaluating {} enabled alert rules", rules.size());

        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("Failed to evaluate alert rule {}: {}", rule.id(), e.getMessage());
            }
        }
    }

    private void evaluateRule(AlertRule rule) {
        switch (rule.alertType()) {
            case "authenticity_drop" -> evaluateAuthenticityDrop(rule);
            case "growth_anomaly" -> evaluateGrowthAnomaly(rule);
            case "viewer_change" -> evaluateViewerChange(rule);
            default -> log.warn("Unknown alert type: {}", rule.alertType());
        }
    }

    private void evaluateAuthenticityDrop(AlertRule rule) {
        if (rule.thresholdValue() == null) return;

        channelAuthenticityRepository.findByChannelId(rule.channelId()).ifPresent(ca -> {
            if (ca.avgAuthenticityScore() != null && ca.avgAuthenticityScore() < rule.thresholdValue()) {
                if (hasUnacknowledgedEvent(rule)) return;

                String severity = ca.avgAuthenticityScore() < 30 ? "critical"
                        : ca.avgAuthenticityScore() < 50 ? "warning" : "info";

                alertRepository.saveEvent(new AlertEvent(
                        null,
                        rule.id(),
                        rule.channelId(),
                        rule.alertType(),
                        String.format("Authenticity score dropped to %.0f, below threshold of %.0f",
                                ca.avgAuthenticityScore(), rule.thresholdValue()),
                        severity,
                        false,
                        null
                ));
                log.info("Fired authenticity_drop alert for channelId {}", rule.channelId());
            }
        });
    }

    private void evaluateGrowthAnomaly(AlertRule rule) {
        List<SocialBladeDailyPoint> daily = socialBladeRepository.findDailyByChannelId(rule.channelId(), 90);
        if (daily.size() < 7) return;

        // Calculate average daily follower change
        double avgChange = daily.stream()
                .filter(d -> d.followerChange() != null)
                .mapToInt(SocialBladeDailyPoint::followerChange)
                .average()
                .orElse(0);

        if (avgChange == 0) return;

        // Check for days where follower change exceeds 3x the average
        boolean hasAnomaly = daily.stream()
                .filter(d -> d.followerChange() != null)
                .limit(7) // check only last 7 days
                .anyMatch(d -> Math.abs(d.followerChange()) > Math.abs(avgChange) * 3);

        if (hasAnomaly) {
            if (hasUnacknowledgedEvent(rule)) return;

            alertRepository.saveEvent(new AlertEvent(
                    null,
                    rule.id(),
                    rule.channelId(),
                    rule.alertType(),
                    String.format("Unusual follower growth detected. Recent growth exceeds 3x the average daily change of %.0f",
                            avgChange),
                    "warning",
                    false,
                    null
            ));
            log.info("Fired growth_anomaly alert for channelId {}", rule.channelId());
        }
    }

    private void evaluateViewerChange(AlertRule rule) {
        // Query recent sessions vs older sessions for peak viewer count
        var sessionTable = table(name("chat", "session_summary"));

        List<Integer> recentViewers = dsl.select(field("peak_viewer_count", Integer.class))
                .from(sessionTable)
                .where(field("channel_id").eq(rule.channelId()))
                .and(field("peak_viewer_count").isNotNull())
                .orderBy(field("start_time").desc())
                .limit(5)
                .fetchInto(Integer.class);

        List<Integer> olderViewers = dsl.select(field("peak_viewer_count", Integer.class))
                .from(sessionTable)
                .where(field("channel_id").eq(rule.channelId()))
                .and(field("peak_viewer_count").isNotNull())
                .orderBy(field("start_time").desc())
                .limit(5)
                .offset(5)
                .fetchInto(Integer.class);

        if (recentViewers.size() < 3 || olderViewers.size() < 3) return;

        double recentAvg = recentViewers.stream().mapToInt(Integer::intValue).average().orElse(0);
        double olderAvg = olderViewers.stream().mapToInt(Integer::intValue).average().orElse(0);

        if (olderAvg == 0) return;

        double changePct = ((recentAvg - olderAvg) / olderAvg) * 100;

        if (Math.abs(changePct) > 30) {
            if (hasUnacknowledgedEvent(rule)) return;

            String direction = changePct > 0 ? "increased" : "decreased";
            String severity = Math.abs(changePct) > 60 ? "critical"
                    : Math.abs(changePct) > 40 ? "warning" : "info";

            alertRepository.saveEvent(new AlertEvent(
                    null,
                    rule.id(),
                    rule.channelId(),
                    rule.alertType(),
                    String.format("Average peak viewers %s by %.0f%% (from %.0f to %.0f)",
                            direction, Math.abs(changePct), olderAvg, recentAvg),
                    severity,
                    false,
                    null
            ));
            log.info("Fired viewer_change alert for channelId {}", rule.channelId());
        }
    }

    private boolean hasUnacknowledgedEvent(AlertRule rule) {
        List<AlertEvent> existing = alertRepository.findEventsByChannelId(rule.channelId(), 100);
        return existing.stream()
                .anyMatch(e -> e.alertRuleId() != null
                        && e.alertRuleId().equals(rule.id())
                        && !e.acknowledged());
    }
}
