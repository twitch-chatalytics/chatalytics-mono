package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.services.wrapper.GlobalStatsService;
import space.forloop.chatalytics.api.services.wrapper.PublicStatsService;
import space.forloop.chatalytics.api.services.wrapper.SessionListService;
import space.forloop.chatalytics.api.services.wrapper.StreamRecapService;
import space.forloop.chatalytics.data.domain.FeaturedChannel;
import space.forloop.chatalytics.data.domain.SessionSummaryView;
import space.forloop.chatalytics.data.repositories.UserRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatsCacheWarmupTask {

    private final UserRepository userRepository;
    private final PublicStatsService publicStatsService;
    private final GlobalStatsService globalStatsService;
    private final SessionListService sessionListService;
    private final StreamRecapService streamRecapService;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        runFullWarmup();
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void warmHourly() {
        runFullWarmup();
    }

    private void runFullWarmup() {
        // 1. Warm per-channel stats (reads from Redis cache, only hits DB on miss)
        warmChannelStats();

        // 2. Recompute global stats + featured channel selection
        globalStatsService.aggregate();

        // 3. Pre-cache session lists and latest recaps for featured channels
        warmFeaturedChannelData();
    }

    private void warmChannelStats() {
        var channels = userRepository.findAll();
        log.info("Warming stats cache for {} channels", channels.size());
        for (var channel : channels) {
            try {
                publicStatsService.getStats(channel.getId());
            } catch (Exception e) {
                log.warn("Failed to warm stats for channelId {}: {}", channel.getId(), e.getMessage());
            }
        }
        log.info("Stats cache warmup complete");
    }

    private void warmFeaturedChannelData() {
        List<FeaturedChannel> featured = globalStatsService.getFeaturedChannels();
        if (featured.isEmpty()) return;

        log.info("Pre-caching data for {} featured channels", featured.size());

        for (FeaturedChannel fc : featured) {
            long channelId = fc.channel().id();
            try {
                // Pre-cache the session list (first page)
                List<SessionSummaryView> sessions = sessionListService.findFirstPage(channelId, 20);

                // Pre-cache recaps for the latest sessions
                int recapCount = 0;
                for (SessionSummaryView session : sessions) {
                    if (recapCount >= 5) break; // top 5 sessions per channel
                    try {
                        streamRecapService.generateRecap(session.sessionId());
                        streamRecapService.fetchFreshClips(session.sessionId(), 8);
                        recapCount++;
                    } catch (Exception e) {
                        log.debug("Failed to warm recap for session {}: {}", session.sessionId(), e.getMessage());
                    }
                }

                log.info("Pre-cached {} sessions and {} recaps for {}", sessions.size(), recapCount, fc.channel().displayName());
            } catch (Exception e) {
                log.warn("Failed to warm data for channel {}: {}", fc.channel().displayName(), e.getMessage());
            }
        }

        log.info("Featured channel data pre-caching complete");
    }
}
