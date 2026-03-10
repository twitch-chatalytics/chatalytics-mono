package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.services.AuthenticityAggregationService;
import space.forloop.chatalytics.api.services.AuthenticityScoreService;
import space.forloop.chatalytics.data.domain.SessionAuthenticity;
import space.forloop.chatalytics.data.repositories.SessionAuthenticityRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticityScoreTask {

    private final SessionAuthenticityRepository sessionAuthenticityRepository;
    private final AuthenticityScoreService authenticityScoreService;
    private final AuthenticityAggregationService aggregationService;

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    public void computeAuthenticityScores() {
        // Always check for missing channel rollups first
        backfillMissingChannelRollups();

        List<Long> sessionIds = sessionAuthenticityRepository.findSessionIdsWithoutAuthenticity();
        if (sessionIds.isEmpty()) return;

        log.info("Found {} sessions without authenticity scores", sessionIds.size());

        Set<Long> channelsToUpdate = new HashSet<>();

        for (long sessionId : sessionIds) {
            try {
                SessionAuthenticity score = authenticityScoreService.computeScore(sessionId);
                if (score != null) {
                    sessionAuthenticityRepository.save(score);
                    channelsToUpdate.add(score.channelId());
                    log.info("Computed authenticity score {} ({}) for session {}",
                            score.authenticityScore(), score.confidenceLevel(), sessionId);
                }
            } catch (Exception e) {
                log.error("Failed to compute authenticity for session {}: {}", sessionId, e.getMessage());
            }
        }

        // Update channel rollups for newly scored sessions
        for (long channelId : channelsToUpdate) {
            try {
                aggregationService.updateChannelRollup(channelId);
            } catch (Exception e) {
                log.error("Failed to update channel rollup for {}: {}", channelId, e.getMessage());
            }
        }
    }

    private void backfillMissingChannelRollups() {
        List<Long> missingRollups = sessionAuthenticityRepository.findChannelIdsWithoutChannelRollup();
        if (missingRollups.isEmpty()) return;

        log.info("Backfilling channel rollups for {} channels", missingRollups.size());
        for (long channelId : missingRollups) {
            try {
                aggregationService.updateChannelRollup(channelId);
                log.info("Backfilled channel rollup for channelId {}", channelId);
            } catch (Exception e) {
                log.error("Failed to backfill channel rollup for {}: {}", channelId, e.getMessage());
            }
        }
    }
}
