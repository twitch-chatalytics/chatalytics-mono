package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelStats;
import space.forloop.chatalytics.data.domain.TopChatter;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.MessageRepository;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.chatalytics.data.repositories.UserRepository;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalStatsService {

    private static final String REDIS_KEY = "globalStats";

    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
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

    @Scheduled(initialDelay = 0, fixedRate = 24, timeUnit = TimeUnit.HOURS)
    public void aggregate() {
        log.info("Aggregating global stats");
        try {
            List<User> users = userRepository.findAll();

            long totalMessages = 0;
            long totalChatters = 0;
            long totalStreams = 0;
            Map<String, Long> chatterCounts = new HashMap<>();

            for (User user : users) {
                long uid = user.getId();
                totalMessages += messageRepository.countAllMessages(uid);
                totalChatters += messageRepository.countDistinctAuthors(uid);
                totalStreams += sessionRepository.countByUserId(uid);

                List<TopChatter> top = messageRepository.topChatters(uid, 20);
                for (TopChatter tc : top) {
                    chatterCounts.merge(tc.author(), (long) tc.messageCount(), Long::sum);
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
            log.info("Global stats aggregated: {} messages, {} chatters, {} streams across {} channels",
                    totalMessages, totalChatters, totalStreams, users.size());
        } catch (Exception e) {
            log.error("Failed to aggregate global stats", e);
        }
    }
}
