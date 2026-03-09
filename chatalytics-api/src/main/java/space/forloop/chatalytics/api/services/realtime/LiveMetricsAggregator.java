package space.forloop.chatalytics.api.services.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.IrcPayload;
import space.forloop.chatalytics.data.domain.LiveMetrics;
import space.forloop.chatalytics.data.domain.TopWord;
import space.forloop.chatalytics.data.repositories.MessageRepository;
import space.forloop.chatalytics.data.repositories.StreamSnapshotRepository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveMetricsAggregator {

    private static final int WINDOW_SECONDS = 60;
    private static final int TOP_WORDS_LIMIT = 10;
    private static final double HYPE_THRESHOLD = 2.0;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final StreamSnapshotRepository streamSnapshotRepository;
    private final MessageRepository messageRepository;

    private final ConcurrentHashMap<Long, StreamWindow> windows = new ConcurrentHashMap<>();

    @KafkaListener(topics = "raw-messages", groupId = "chatalytics-api-live")
    public void onMessage(String payload) {
        try {
            IrcPayload irc = objectMapper.readValue(payload, IrcPayload.class);
            if (irc.getSession() == null) return;

            long twitchId = irc.getSession().twitchId();
            long sessionId = irc.getSession().id();

            StreamWindow window = windows.computeIfAbsent(twitchId, k -> {
                long baseMessages = messageRepository.countMessagesBySessionId(sessionId);
                long baseChatters = messageRepository.countChattersBySessionId(sessionId);
                return new StreamWindow(sessionId, baseMessages, baseChatters);
            });
            window.record(irc);
        } catch (JsonProcessingException e) {
            log.debug("Failed to parse IrcPayload: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "raw-sessions-online", groupId = "chatalytics-api-live")
    public void onSessionOnline(String payload) {
        log.info("Live metrics: session started");
    }

    @KafkaListener(topics = "raw-sessions-offline", groupId = "chatalytics-api-live")
    public void onSessionOffline(String payload) {
        log.info("Live metrics: session ended — will be cleaned up on next publish cycle");
    }

    @Scheduled(fixedRate = 2000)
    public void publishSnapshots() {
        if (windows.isEmpty()) return;

        Instant now = Instant.now();
        Iterator<Map.Entry<Long, StreamWindow>> it = windows.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Long, StreamWindow> entry = it.next();
            long twitchId = entry.getKey();
            StreamWindow window = entry.getValue();

            window.evictOlderThan(now.minusSeconds(WINDOW_SECONDS));

            if (window.isEmpty()) {
                it.remove();
                continue;
            }

            int viewerCount = fetchLatestViewerCount(window.sessionId);
            LiveMetrics metrics = window.snapshot(twitchId, now, viewerCount);

            try {
                String json = objectMapper.writeValueAsString(metrics);
                stringRedisTemplate.convertAndSend("live:metrics:" + twitchId, json);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize LiveMetrics: {}", e.getMessage());
            }
        }
    }

    private int fetchLatestViewerCount(long sessionId) {
        try {
            var snapshots = streamSnapshotRepository.findBySessionId(sessionId);
            if (!snapshots.isEmpty()) {
                return snapshots.get(snapshots.size() - 1).viewerCount();
            }
        } catch (Exception e) {
            log.debug("Failed to fetch viewer count for session {}: {}", sessionId, e.getMessage());
        }
        return 0;
    }

    static class StreamWindow {
        final long sessionId;
        final ConcurrentLinkedDeque<TimestampedMessage> messages = new ConcurrentLinkedDeque<>();
        final ConcurrentHashMap<String, AtomicInteger> wordCounts = new ConcurrentHashMap<>();
        final AtomicLong totalMessageCount;
        final long baselineChatters;
        final Set<String> newChatters = ConcurrentHashMap.newKeySet();
        double rollingAvgMpm = 0;
        int publishCount = 0;

        StreamWindow(long sessionId, long baselineMessages, long baselineChatters) {
            this.sessionId = sessionId;
            this.totalMessageCount = new AtomicLong(baselineMessages);
            this.baselineChatters = baselineChatters;
        }

        void record(IrcPayload irc) {
            Instant ts = irc.getTimestamp() != null ? irc.getTimestamp() : Instant.now();
            messages.addLast(new TimestampedMessage(ts, irc.getNick(), irc.getMessage()));
            totalMessageCount.incrementAndGet();
            if (irc.getNick() != null) newChatters.add(irc.getNick());

            if (irc.getMessage() != null) {
                for (String word : irc.getMessage().split("\\s+")) {
                    String w = word.toLowerCase();
                    if (w.length() >= 2) {
                        wordCounts.computeIfAbsent(w, k -> new AtomicInteger()).incrementAndGet();
                    }
                }
            }
        }

        void evictOlderThan(Instant cutoff) {
            while (!messages.isEmpty() && messages.peekFirst().timestamp.isBefore(cutoff)) {
                TimestampedMessage removed = messages.pollFirst();
                if (removed != null && removed.message != null) {
                    for (String word : removed.message.split("\\s+")) {
                        String w = word.toLowerCase();
                        if (w.length() >= 2) {
                            AtomicInteger count = wordCounts.get(w);
                            if (count != null && count.decrementAndGet() <= 0) {
                                wordCounts.remove(w);
                            }
                        }
                    }
                }
            }
        }

        boolean isEmpty() {
            return messages.isEmpty();
        }

        LiveMetrics snapshot(long twitchId, Instant now, int viewerCount) {
            int messageCount = messages.size();
            double mpm = messageCount; // messages in last 60s ≈ messages per minute

            // Smooth rolling average for hype detection
            publishCount++;
            if (publishCount == 1) {
                rollingAvgMpm = mpm;
            } else {
                rollingAvgMpm = rollingAvgMpm * 0.9 + mpm * 0.1;
            }

            double hypeMultiplier = rollingAvgMpm > 0 ? mpm / rollingAvgMpm : 1.0;
            boolean isHype = hypeMultiplier >= HYPE_THRESHOLD;

            Set<String> chatters = new HashSet<>();
            for (TimestampedMessage msg : messages) {
                if (msg.nick != null) chatters.add(msg.nick);
            }

            List<TopWord> topWords = wordCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(
                            Comparator.comparingInt(AtomicInteger::get)).reversed())
                    .limit(TOP_WORDS_LIMIT)
                    .map(e -> new TopWord(e.getKey(), e.getValue().get()))
                    .toList();

            return new LiveMetrics(
                    twitchId,
                    sessionId,
                    now,
                    Math.round(mpm * 10.0) / 10.0,
                    chatters.size(),
                    viewerCount,
                    isHype,
                    Math.round(hypeMultiplier * 100.0) / 100.0,
                    topWords,
                    totalMessageCount.get(),
                    (int) (baselineChatters + newChatters.size())
            );
        }

        record TimestampedMessage(Instant timestamp, String nick, String message) {}
    }
}
