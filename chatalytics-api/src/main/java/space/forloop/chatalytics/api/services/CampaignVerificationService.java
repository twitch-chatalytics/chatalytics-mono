package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.Campaign;
import space.forloop.chatalytics.data.domain.CampaignReport;
import space.forloop.chatalytics.data.repositories.CampaignRepository;
import space.forloop.chatalytics.data.repositories.SessionAuthenticityRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.jooq.impl.DSL.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignVerificationService {

    private final CampaignRepository campaignRepository;
    private final SessionAuthenticityRepository sessionAuthenticityRepository;
    private final DSLContext dsl;

    public CampaignReport generateReport(long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        // Find sessions that overlap with campaign date range
        List<Long> sponsoredSessionIds = findSessionsInDateRange(
                campaign.twitchId(), campaign.startDate(), campaign.endDate());

        int sponsoredSessions = sponsoredSessionIds.size();

        // Compute sponsored avg authenticity score
        double sponsoredAvgScore = 0;
        if (!sponsoredSessionIds.isEmpty()) {
            var authTable = table(name("twitch", "session_authenticity"));
            Double avg = dsl.select(avg(field("authenticity_score", Double.class)))
                    .from(authTable)
                    .where(field("session_id").in(sponsoredSessionIds))
                    .fetchOneInto(Double.class);
            sponsoredAvgScore = avg != null ? avg : 0;
        }

        // Compute baseline avg score (all other sessions for this channel)
        double baselineAvgScore = 0;
        {
            var authTable = table(name("twitch", "session_authenticity"));
            var condition = field("twitch_id").eq(campaign.twitchId());
            if (!sponsoredSessionIds.isEmpty()) {
                condition = condition.and(field("session_id").notIn(sponsoredSessionIds));
            }
            Double avg = dsl.select(avg(field("authenticity_score", Double.class)))
                    .from(authTable)
                    .where(condition)
                    .fetchOneInto(Double.class);
            baselineAvgScore = avg != null ? avg : 0;
        }

        double scoreDelta = sponsoredAvgScore - baselineAvgScore;

        // Count total messages and brand keyword mentions
        int totalMessages = 0;
        int brandMentions = 0;
        if (!sponsoredSessionIds.isEmpty()) {
            var msgTable = table(name("twitch", "message"));
            Long msgCount = dsl.selectCount()
                    .from(msgTable)
                    .where(field("session_id").in(sponsoredSessionIds))
                    .fetchOneInto(Long.class);
            totalMessages = msgCount != null ? msgCount.intValue() : 0;

            brandMentions = countBrandMentions(sponsoredSessionIds, campaign.brandKeywords());
        }

        double brandMentionRate = totalMessages > 0 ? (double) brandMentions / totalMessages : 0;

        // Calculate real impressions: sum of (viewer_count * score/100) per session
        double estimatedRealImpressions = 0;
        if (!sponsoredSessionIds.isEmpty()) {
            var recapTable = table(name("twitch", "stream_recap"));
            var authTable = table(name("twitch", "session_authenticity"));

            var rows = dsl.select(
                            field(name("twitch", "stream_recap", "peak_viewer_count"), Integer.class),
                            field(name("twitch", "session_authenticity", "authenticity_score"), Integer.class)
                    )
                    .from(recapTable)
                    .join(authTable)
                    .on(field(name("twitch", "stream_recap", "session_id"))
                            .eq(field(name("twitch", "session_authenticity", "session_id"))))
                    .where(field(name("twitch", "stream_recap", "session_id")).in(sponsoredSessionIds))
                    .and(field(name("twitch", "stream_recap", "peak_viewer_count")).isNotNull())
                    .fetch();

            for (var r : rows) {
                Integer viewers = r.get(0, Integer.class);
                Integer score = r.get(1, Integer.class);
                if (viewers != null && score != null) {
                    estimatedRealImpressions += viewers * (score / 100.0);
                }
            }
        }

        // CPM calculations
        Double reportedCpm = null;
        Double realCpm = null;
        if (campaign.dealPrice() != null && campaign.dealPrice() > 0) {
            // Reported CPM based on raw viewer counts
            double totalReportedImpressions = 0;
            if (!sponsoredSessionIds.isEmpty()) {
                var recapTable = table(name("twitch", "stream_recap"));
                Long sumViewers = dsl.select(sum(field("peak_viewer_count", Long.class)))
                        .from(recapTable)
                        .where(field("session_id").in(sponsoredSessionIds))
                        .and(field("peak_viewer_count").isNotNull())
                        .fetchOneInto(Long.class);
                totalReportedImpressions = sumViewers != null ? sumViewers : 0;
            }

            if (totalReportedImpressions > 0) {
                reportedCpm = (campaign.dealPrice() / totalReportedImpressions) * 1000;
            }
            if (estimatedRealImpressions > 0) {
                realCpm = (campaign.dealPrice() / estimatedRealImpressions) * 1000;
            }
        }

        return new CampaignReport(
                campaign,
                sponsoredSessions,
                Math.round(sponsoredAvgScore * 10) / 10.0,
                Math.round(baselineAvgScore * 10) / 10.0,
                Math.round(scoreDelta * 10) / 10.0,
                totalMessages,
                brandMentions,
                Math.round(brandMentionRate * 10000) / 10000.0,
                Math.round(estimatedRealImpressions),
                reportedCpm != null ? Math.round(reportedCpm * 100) / 100.0 : null,
                realCpm != null ? Math.round(realCpm * 100) / 100.0 : null
        );
    }

    private List<Long> findSessionsInDateRange(long twitchId, LocalDate start, LocalDate end) {
        var sessionTable = table(name("twitch", "session"));
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(LocalTime.MAX);

        return dsl.select(field("id", Long.class))
                .from(sessionTable)
                .where(field("twitch_id").eq(twitchId))
                .and(field("start_time").greaterOrEqual(startDateTime))
                .and(field("start_time").lessOrEqual(endDateTime))
                .fetchInto(Long.class);
    }

    private int countBrandMentions(List<Long> sessionIds, List<String> keywords) {
        if (keywords == null || keywords.isEmpty() || sessionIds.isEmpty()) return 0;

        var msgTable = table(name("twitch", "message"));

        // Build case-insensitive LIKE conditions for each keyword
        var condition = field("session_id").in(sessionIds);
        var keywordCondition = noCondition();
        for (String keyword : keywords) {
            keywordCondition = keywordCondition.or(
                    lower(field("message_text", String.class)).like("%" + keyword.toLowerCase() + "%")
            );
        }

        Long count = dsl.selectCount()
                .from(msgTable)
                .where(condition)
                .and(keywordCondition)
                .fetchOneInto(Long.class);

        return count != null ? count.intValue() : 0;
    }
}
