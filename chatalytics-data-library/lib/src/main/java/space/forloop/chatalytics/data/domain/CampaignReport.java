package space.forloop.chatalytics.data.domain;

import java.io.Serializable;

public record CampaignReport(
        Campaign campaign,
        int sponsoredSessions,
        double sponsoredAvgScore,
        double baselineAvgScore,
        double scoreDelta,
        int totalMessages,
        int brandMentions,
        double brandMentionRate,
        double estimatedRealImpressions,
        Double reportedCpm,
        Double realCpm
) implements Serializable {}
