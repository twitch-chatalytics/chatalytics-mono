package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelAuthenticity;
import space.forloop.chatalytics.data.domain.SessionAuthenticity;
import space.forloop.chatalytics.data.repositories.ChannelAuthenticityRepository;
import space.forloop.chatalytics.data.repositories.SessionAuthenticityRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticityAggregationService {

    private final SessionAuthenticityRepository sessionAuthenticityRepository;
    private final ChannelAuthenticityRepository channelAuthenticityRepository;

    public void updateChannelRollup(long channelId) {
        List<SessionAuthenticity> sessions = sessionAuthenticityRepository.findByChannelId(channelId, 100, 0);
        if (sessions.isEmpty()) return;

        double avg = sessions.stream()
                .mapToInt(SessionAuthenticity::authenticityScore)
                .average()
                .orElse(0);

        int min = sessions.stream()
                .mapToInt(SessionAuthenticity::authenticityScore)
                .min()
                .orElse(0);

        int max = sessions.stream()
                .mapToInt(SessionAuthenticity::authenticityScore)
                .max()
                .orElse(0);

        // Compute trend from last 10 vs previous 10
        String trend = computeTrend(sessions);

        // Determine risk level
        String riskLevel = avg >= 70 ? "low" : avg >= 40 ? "medium" : "high";

        // Collect risk factors
        List<String> riskFactors = new ArrayList<>();
        if (avg < 40) {
            riskFactors.add("Consistently low authenticity scores");
        }
        if (min < 20) {
            riskFactors.add("Sessions with very low authenticity detected");
        }
        if ("declining".equals(trend)) {
            riskFactors.add("Authenticity trend is declining");
        }

        channelAuthenticityRepository.save(new ChannelAuthenticity(
                channelId, avg, min, max, trend,
                sessions.size(), riskLevel, riskFactors, Instant.now()
        ));
    }

    private String computeTrend(List<SessionAuthenticity> sessions) {
        if (sessions.size() < 4) return "stable";

        int halfSize = Math.min(sessions.size() / 2, 10);

        // sessions are ordered desc by computed_at, so first entries are most recent
        double recentAvg = sessions.subList(0, halfSize).stream()
                .mapToInt(SessionAuthenticity::authenticityScore)
                .average()
                .orElse(0);

        double olderAvg = sessions.subList(halfSize, Math.min(halfSize * 2, sessions.size())).stream()
                .mapToInt(SessionAuthenticity::authenticityScore)
                .average()
                .orElse(0);

        double diff = recentAvg - olderAvg;
        if (diff > 5) return "improving";
        if (diff < -5) return "declining";
        return "stable";
    }
}
