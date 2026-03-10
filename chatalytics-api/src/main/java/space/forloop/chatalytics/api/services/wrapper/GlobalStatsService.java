package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelProfile;
import space.forloop.chatalytics.data.domain.ChannelStats;
import space.forloop.chatalytics.data.domain.FeaturedChannel;
import space.forloop.chatalytics.data.domain.TopChatter;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.UserRepository;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Global stats and featured channel aggregation.
 * Called by {@link space.forloop.chatalytics.api.tasks.StatsCacheWarmupTask} on startup and hourly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalStatsService {

    private static final String REDIS_KEY = "globalStats";
    private static final String FEATURED_KEY = "featuredChannels";

    private final UserRepository userRepository;
    private final PublicStatsService publicStatsService;
    private final RedisTemplate<String, Object> redisTemplate;

    public record GlobalStats(
            long totalMessages,
            long uniqueChatters,
            long totalStreams,
            int trackedChannels,
            List<TopChatter> topChatters,
            String updatedAt
    ) implements Serializable {}

    public Optional<GlobalStats> getStats() {
        Object cached = redisTemplate.opsForValue().get(REDIS_KEY);
        if (cached instanceof GlobalStats gs) {
            return Optional.of(gs);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public List<FeaturedChannel> getFeaturedChannels() {
        Object cached = redisTemplate.opsForValue().get(FEATURED_KEY);
        if (cached instanceof List<?> list) {
            return (List<FeaturedChannel>) list;
        }
        return List.of();
    }

    public void aggregate() {
        log.info("Aggregating global stats");
        try {
            List<User> users = userRepository.findAll();

            long totalMessages = 0;
            long totalChatters = 0;
            long totalStreams = 0;
            Map<String, Long> chatterCounts = new HashMap<>();
            List<FeaturedChannel> candidates = new ArrayList<>();

            for (User user : users) {
                long uid = user.getId();
                try {
                    // Uses Redis-cached stats (warmed by StatsCacheWarmupTask)
                    ChannelStats cs = publicStatsService.getStats(uid);

                    totalMessages += cs.totalMessages();
                    totalChatters += cs.uniqueChatters();
                    totalStreams += cs.totalSessions();

                    for (TopChatter tc : cs.topChatters()) {
                        chatterCounts.merge(tc.author(), (long) tc.messageCount(), Long::sum);
                    }

                    if (cs.totalMessages() > 0) {
                        ChannelProfile profile = new ChannelProfile(
                                uid, user.getLogin(), user.getDisplayName(),
                                user.getBroadcasterType(), user.getDescription(),
                                user.getProfileImageUrl(), user.getOfflineImageUrl(),
                                user.getCreatedAt());
                        candidates.add(new FeaturedChannel(profile, cs));
                    }
                } catch (Exception e) {
                    log.warn("Failed to get stats for channel {}: {}", uid, e.getMessage());
                }
            }

            List<TopChatter> globalTop = chatterCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .map(e -> new TopChatter(e.getKey(), e.getValue().intValue()))
                    .toList();

            GlobalStats stats = new GlobalStats(
                    totalMessages,
                    totalChatters,
                    totalStreams,
                    users.size(),
                    globalTop,
                    Instant.now().toString()
            );

            redisTemplate.opsForValue().set(REDIS_KEY, stats, 25, TimeUnit.HOURS);

            // Top 8 by message count for featured section
            List<FeaturedChannel> featured = candidates.stream()
                    .sorted(Comparator.comparingLong((FeaturedChannel f) -> f.stats().totalMessages()).reversed())
                    .limit(8)
                    .toList();

            redisTemplate.opsForValue().set(FEATURED_KEY, (Serializable) new ArrayList<>(featured), 25, TimeUnit.HOURS);

            log.info("Global stats aggregated: {} messages, {} chatters, {} streams across {} channels, {} featured",
                    totalMessages, totalChatters, totalStreams, users.size(), featured.size());
        } catch (Exception e) {
            log.error("Failed to aggregate global stats", e);
        }
    }
}
