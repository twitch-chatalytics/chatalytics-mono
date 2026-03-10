package space.forloop.chatalytics.api.services.wrapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import space.forloop.chatalytics.data.domain.*;
import space.forloop.chatalytics.data.generated.tables.pojos.Message;
import space.forloop.chatalytics.data.repositories.MessageRepository;
import space.forloop.chatalytics.data.repositories.MessageWordRepository;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.chatalytics.data.repositories.StreamRecapRepository;
import space.forloop.chatalytics.data.repositories.StreamSnapshotRepository;
import space.forloop.chatalytics.twitch.model.TwitchClipData;
import space.forloop.chatalytics.twitch.service.TwitchService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static space.forloop.chatalytics.api.util.CacheConstants.*;

@Slf4j
@Service
public class StreamRecapService {

    private static final Executor QUERY_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final MessageWordRepository messageWordRepository;
    private final StreamSnapshotRepository snapshotRepository;
    private final StreamRecapRepository streamRecapRepository;
    private final TwitchService twitchService;
    private final CacheManager cacheManager;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public StreamRecapService(
            SessionRepository sessionRepository,
            MessageRepository messageRepository,
            MessageWordRepository messageWordRepository,
            StreamSnapshotRepository snapshotRepository,
            StreamRecapRepository streamRecapRepository,
            TwitchService twitchService,
            CacheManager cacheManager,
            @Value("${anthropic.api-key:}") String apiKey) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.messageWordRepository = messageWordRepository;
        this.snapshotRepository = snapshotRepository;
        this.streamRecapRepository = streamRecapRepository;
        this.twitchService = twitchService;
        this.cacheManager = cacheManager;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
    }

    /**
     * Returns a recap, using a tiered caching strategy:
     * 1. Persisted in DB (ended streams, permanent)
     * 2. Redis ended-stream cache (6h TTL)
     * 3. Redis live-stream cache (60s TTL, sync to prevent thundering herd)
     *
     * AI summary is generated asynchronously — the recap is returned immediately
     * without it, and the cache is updated once the summary is ready.
     */
    public StreamRecap generateRecap(long sessionId) {
        // 1. Check DB for persisted recap (ended streams)
        var persisted = streamRecapRepository.findBySessionId(sessionId);
        if (persisted.isPresent()) {
            return persisted.get();
        }

        // 2. Check ended-stream Redis cache
        Cache endedCache = cacheManager.getCache(STREAM_RECAP);
        if (endedCache != null) {
            StreamRecap cached = endedCache.get(sessionId, StreamRecap.class);
            if (cached != null) return cached;
        }

        // 3. Use live cache with sync (Callable variant prevents thundering herd)
        Cache liveCache = cacheManager.getCache(STREAM_RECAP_LIVE);
        if (liveCache != null) {
            return liveCache.get(sessionId, () -> computeAndCacheAsync(sessionId));
        }

        // Fallback if cache not available
        return computeAndCacheAsync(sessionId);
    }

    /**
     * Computes recap without blocking on AI summary.
     * Fires AI summary generation in background and updates cache when done.
     */
    private StreamRecap computeAndCacheAsync(long sessionId) {
        StreamRecap recap = computeRecapFast(sessionId);
        if (recap == null) return null;

        // If the stream has ended, cache in the long-TTL cache
        if (recap.endTime() != null) {
            Cache endedCache = cacheManager.getCache(STREAM_RECAP);
            if (endedCache != null) endedCache.put(sessionId, recap);
        }

        // Fire AI summary generation in background
        if (recap.aiSummary() == null && apiKey != null && !apiKey.isBlank()) {
            final StreamRecap recapForAsync = recap;
            CompletableFuture.runAsync(() -> {
                try {
                    String summary = generateAiSummary(sessionId, recapForAsync);
                    if (summary != null) {
                        StreamRecap withSummary = recapForAsync.withAiSummary(summary);
                        // Update the appropriate cache
                        if (recapForAsync.endTime() != null) {
                            Cache ended = cacheManager.getCache(STREAM_RECAP);
                            if (ended != null) ended.put(sessionId, withSummary);
                        } else {
                            Cache live = cacheManager.getCache(STREAM_RECAP_LIVE);
                            if (live != null) live.put(sessionId, withSummary);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Async AI summary failed for session {}: {}", sessionId, e.getMessage());
                }
            }, QUERY_EXECUTOR);
        }

        return recap;
    }

    /**
     * Fetches clips from Twitch for a session, cached for 24h in Redis.
     */
    @Cacheable(value = STREAM_CLIPS, key = "#sessionId + ':' + #limit", sync = true)
    public List<StreamClip> fetchFreshClips(long sessionId, int limit) {
        var session = sessionRepository.findByIdWithUser(sessionId);
        if (session.isEmpty()) return List.of();
        return fetchTopClips(session.get(), limit);
    }

    /**
     * Computes a full recap from source data, WITHOUT the AI summary.
     * Returns immediately after DB queries complete.
     */
    private StreamRecap computeRecapFast(long sessionId) {
        var sessionOpt = sessionRepository.findByIdWithUser(sessionId);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        SessionWithUser session = sessionOpt.get();

        // Fire all independent queries in parallel using virtual threads
        var totalMessagesFuture = CompletableFuture.supplyAsync(
                () -> messageRepository.countMessagesBySessionId(sessionId), QUERY_EXECUTOR);
        var totalChattersFuture = CompletableFuture.supplyAsync(
                () -> messageRepository.countChattersBySessionId(sessionId), QUERY_EXECUTOR);
        var snapshotsFuture = CompletableFuture.supplyAsync(
                () -> snapshotRepository.findBySessionId(sessionId), QUERY_EXECUTOR);
        var chatActivityFuture = CompletableFuture.supplyAsync(
                () -> messageRepository.chatActivityBySessionId(sessionId, 5), QUERY_EXECUTOR);
        var topChattersFuture = CompletableFuture.supplyAsync(
                () -> messageRepository.topChattersBySessionId(sessionId, 10), QUERY_EXECUTOR);
        var messageAnalysisFuture = CompletableFuture.supplyAsync(
                () -> messageRepository.messageAnalysisBySessionId(sessionId), QUERY_EXECUTOR);
        var newChatterCountFuture = CompletableFuture.supplyAsync(
                () -> messageRepository.newChatterCountBySessionId(sessionId), QUERY_EXECUTOR);
        var topWordsFuture = CompletableFuture.supplyAsync(
                () -> messageWordRepository.topWordsBySessionId(sessionId, 20), QUERY_EXECUTOR);
        var clipsFuture = CompletableFuture.supplyAsync(
                () -> fetchTopClips(session, 8), QUERY_EXECUTOR);

        // Game segments depend on snapshots — chain on that future
        var gameSegmentsFuture = snapshotsFuture.thenApplyAsync(
                snaps -> computeGameSegments(sessionId, snaps), QUERY_EXECUTOR);

        // Wait for all queries to complete (NO sample messages needed without AI summary)
        CompletableFuture.allOf(
                totalMessagesFuture, totalChattersFuture, snapshotsFuture,
                chatActivityFuture, topChattersFuture, messageAnalysisFuture,
                newChatterCountFuture, topWordsFuture, clipsFuture,
                gameSegmentsFuture
        ).join();

        // Extract results
        long totalMessages = totalMessagesFuture.join();
        long totalChatters = totalChattersFuture.join();
        List<StreamSnapshot> snapshots = snapshotsFuture.join();
        List<ChatActivityBucket> chatActivity = chatActivityFuture.join();
        List<TopChatter> topChatters = topChattersFuture.join();
        MessageAnalysis messageAnalysis = messageAnalysisFuture.join();
        long newChatterCount = newChatterCountFuture.join();
        List<TopWord> topWords = topWordsFuture.join();
        List<StreamClip> topClips = clipsFuture.join();
        List<GameSegment> gameSegments = gameSegmentsFuture.join();

        // Velocity metrics (in-memory)
        long durationMinutes = session.endTime() != null
                ? Duration.between(session.startTime(), session.endTime()).toMinutes()
                : Duration.between(session.startTime(), Instant.now()).toMinutes();
        double messagesPerMinute = durationMinutes > 0 ? (double) totalMessages / durationMinutes : 0;
        double chattersPerMinute = durationMinutes > 0 ? (double) totalChatters / durationMinutes : 0;

        // Viewer metrics from snapshots (in-memory)
        Integer peakViewerCount = snapshots.isEmpty() ? null :
                snapshots.stream().mapToInt(StreamSnapshot::viewerCount).max().orElse(0);
        Double avgViewerCount = snapshots.isEmpty() ? null :
                snapshots.stream().mapToInt(StreamSnapshot::viewerCount).average().orElse(0);
        Integer minViewerCount = snapshots.isEmpty() ? null :
                snapshots.stream().mapToInt(StreamSnapshot::viewerCount).min().orElse(0);

        // Chatter segmentation
        long returningChatterCount = totalChatters - newChatterCount;

        // Engagement rate
        Double chatParticipationRate = (peakViewerCount != null && peakViewerCount > 0)
                ? (double) totalChatters / peakViewerCount : null;

        // Peak moment from chat activity
        ChatMoment peakMoment = chatActivity.isEmpty() ? null :
                chatActivity.stream()
                        .max(Comparator.comparingLong(ChatActivityBucket::messageCount))
                        .map(b -> new ChatMoment(b.bucketStart(), b.messageCount(), b.uniqueChatters()))
                        .orElse(null);

        // Hype moments — chat activity buckets significantly above average
        List<HypeMoment> hypeMoments = List.of();
        if (!chatActivity.isEmpty()) {
            double avgMsgCount = chatActivity.stream()
                    .mapToLong(ChatActivityBucket::messageCount)
                    .average()
                    .orElse(0);
            if (avgMsgCount > 0) {
                hypeMoments = chatActivity.stream()
                        .filter(b -> b.messageCount() >= 2.0 * avgMsgCount)
                        .map(b -> new HypeMoment(b.bucketStart(), b.messageCount(), b.uniqueChatters(),
                                b.messageCount() / avgMsgCount))
                        .sorted(Comparator.comparingDouble(HypeMoment::multiplier).reversed())
                        .limit(5)
                        .toList();
            }
        }

        // No AI summary — it will be generated asynchronously
        return new StreamRecap(
                sessionId,
                session.startTime(),
                session.endTime(),
                totalMessages,
                totalChatters,
                snapshots,
                chatActivity,
                topChatters,
                null, // aiSummary — populated async
                messagesPerMinute,
                chattersPerMinute,
                peakViewerCount,
                avgViewerCount,
                minViewerCount,
                messageAnalysis,
                newChatterCount,
                returningChatterCount,
                topWords,
                gameSegments,
                chatParticipationRate,
                peakMoment,
                topClips,
                hypeMoments
        );
    }

    /**
     * Computes a full recap WITH AI summary. Used by the persistence task.
     */
    public StreamRecap computeRecap(long sessionId) {
        StreamRecap recap = computeRecapFast(sessionId);
        if (recap == null) return null;

        String aiSummary = generateAiSummary(sessionId, recap);
        return recap.withAiSummary(aiSummary);
    }

    private String generateAiSummary(long sessionId, StreamRecap recap) {
        if (apiKey == null || apiKey.isBlank()) return null;

        var sessionOpt = sessionRepository.findByIdWithUser(sessionId);
        if (sessionOpt.isEmpty()) return null;
        SessionWithUser session = sessionOpt.get();

        // Fetch sample messages for context
        List<Message> sampleMessages = messageRepository.findSampleBySessionId(sessionId, 150);

        return generateAiSummary(session, recap.snapshots(), recap.chatActivity(),
                recap.topChatters(), recap.totalMessages(), recap.totalChatters(),
                recap.topWords(), recap.gameSegments(), recap.chatParticipationRate(),
                sampleMessages);
    }

    private List<StreamClip> fetchTopClips(SessionWithUser session, int limit) {
        try {
            String broadcasterId = String.valueOf(session.twitchId());
            Instant endedAt = session.endTime() != null ? session.endTime() : Instant.now();
            List<TwitchClipData> clips = twitchService.findClips(broadcasterId, session.startTime(), endedAt, limit);
            return clips.stream()
                    .map(c -> new StreamClip(c.id(), c.url(), c.embedUrl(), c.title(),
                            c.viewCount(), c.createdAt(), c.thumbnailUrl(), c.duration(), c.creatorName()))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch clips for session {}: {}", session.id(), e.getMessage());
            return List.of();
        }
    }

    private List<GameSegment> computeGameSegments(long sessionId, List<StreamSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return List.of();
        }

        List<GameSegment> segments = new ArrayList<>();
        String currentGame = snapshots.getFirst().gameName();
        Instant segmentStart = snapshots.getFirst().timestamp();
        int segmentPeak = snapshots.getFirst().viewerCount();
        long segmentViewerSum = snapshots.getFirst().viewerCount();
        int segmentSnapshotCount = 1;

        for (int i = 1; i < snapshots.size(); i++) {
            StreamSnapshot snap = snapshots.get(i);
            String game = snap.gameName();

            if (game != null && !game.equals(currentGame)) {
                // Game changed - finalize previous segment
                Instant segmentEnd = snap.timestamp();
                long durationMin = Duration.between(segmentStart, segmentEnd).toMinutes();
                long msgCount = messageRepository.countMessagesBySessionIdAndTimeRange(sessionId, segmentStart, segmentEnd);
                double avgViewers = segmentSnapshotCount > 0 ? (double) segmentViewerSum / segmentSnapshotCount : 0;
                segments.add(new GameSegment(currentGame, segmentStart, segmentEnd, durationMin, msgCount, avgViewers, segmentPeak));

                // Start new segment
                currentGame = game;
                segmentStart = snap.timestamp();
                segmentPeak = snap.viewerCount();
                segmentViewerSum = snap.viewerCount();
                segmentSnapshotCount = 1;
            } else {
                segmentPeak = Math.max(segmentPeak, snap.viewerCount());
                segmentViewerSum += snap.viewerCount();
                segmentSnapshotCount++;
            }
        }

        // Finalize last segment
        Instant segmentEnd = snapshots.getLast().timestamp();
        long durationMin = Duration.between(segmentStart, segmentEnd).toMinutes();
        long msgCount = messageRepository.countMessagesBySessionIdAndTimeRange(sessionId, segmentStart, segmentEnd.plusSeconds(1));
        double avgViewers = segmentSnapshotCount > 0 ? (double) segmentViewerSum / segmentSnapshotCount : 0;
        segments.add(new GameSegment(currentGame, segmentStart, segmentEnd, durationMin, msgCount, avgViewers, segmentPeak));

        return segments;
    }

    private String generateAiSummary(
            SessionWithUser session,
            List<StreamSnapshot> snapshots,
            List<ChatActivityBucket> chatActivity,
            List<TopChatter> topChatters,
            long totalMessages,
            long totalChatters,
            List<TopWord> topWords,
            List<GameSegment> gameSegments,
            Double chatParticipationRate,
            List<Message> sampleMessages) {

        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        // Build context for Claude
        StringBuilder context = new StringBuilder();
        context.append("Stream by: ").append(session.login()).append("\n");
        context.append("Duration: ");
        if (session.endTime() != null) {
            long minutes = Duration.between(session.startTime(), session.endTime()).toMinutes();
            context.append(minutes / 60).append("h ").append(minutes % 60).append("m\n");
        } else {
            context.append("ongoing\n");
        }
        context.append("Total messages: ").append(totalMessages).append("\n");
        context.append("Unique chatters: ").append(totalChatters).append("\n");
        if (chatParticipationRate != null) {
            context.append("Chat participation rate: ").append(String.format("%.1f%%", chatParticipationRate * 100)).append("\n");
        }
        context.append("\n");

        // Game segments with detailed metrics
        if (!gameSegments.isEmpty()) {
            context.append("Game segments:\n");
            for (GameSegment seg : gameSegments) {
                context.append("  ").append(seg.gameName())
                        .append(" (").append(seg.durationMinutes()).append("min")
                        .append(", ").append(seg.messageCount()).append(" msgs")
                        .append(", avg ").append(String.format("%.0f", seg.avgViewers())).append(" viewers")
                        .append(", peak ").append(seg.peakViewers()).append(" viewers)\n");
            }
            context.append("\n");
        } else if (!snapshots.isEmpty()) {
            context.append("Stream segments (game/category over time):\n");
            String currentGame = null;
            for (StreamSnapshot snap : snapshots) {
                if (snap.gameName() != null && !snap.gameName().equals(currentGame)) {
                    currentGame = snap.gameName();
                    context.append("  ").append(snap.timestamp()).append(" - ").append(currentGame)
                            .append(" (").append(snap.viewerCount()).append(" viewers)\n");
                }
            }
            context.append("\n");
        }

        // Top words
        if (!topWords.isEmpty()) {
            context.append("Top words in chat: ");
            context.append(topWords.stream().limit(15)
                    .map(w -> w.word() + "(" + w.count() + ")")
                    .collect(Collectors.joining(", ")));
            context.append("\n\n");
        }

        // Chat activity peaks
        if (!chatActivity.isEmpty()) {
            context.append("Chat activity (5-min buckets, top 10 by messages):\n");
            chatActivity.stream()
                    .sorted((a, b) -> Long.compare(b.messageCount(), a.messageCount()))
                    .limit(10)
                    .forEach(b -> context.append("  ").append(b.bucketStart())
                            .append(": ").append(b.messageCount()).append(" msgs, ")
                            .append(b.uniqueChatters()).append(" chatters\n"));
            context.append("\n");
        }

        // Top chatters
        if (!topChatters.isEmpty()) {
            context.append("Top chatters:\n");
            topChatters.stream().limit(5).forEach(tc ->
                    context.append("  ").append(tc.author()).append(": ").append(tc.messageCount()).append(" msgs\n"));
            context.append("\n");
        }

        // Sample messages from peak moments
        if (!sampleMessages.isEmpty()) {
            context.append("Sample messages from this stream:\n");
            sampleMessages.forEach(m -> context.append("  [").append(m.getAuthor()).append("] ").append(m.getMessageText()).append("\n"));
        }

        String prompt = """
                You are generating a short stream recap for a Twitch streamer. Below is data from one streaming session.
                Write 2-3 sentences that capture the story of this stream. Be punchy and specific — mention the game(s), \
                the vibe, and one standout moment or stat. Write in a casual tone. No bullet points, no headers.

                Stream data:
                %s""".formatted(context.toString());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = Map.of(
                    "model", "claude-haiku-4-5-20251001",
                    "max_tokens", 256,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.anthropic.com/v1/messages",
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("content").get(0).path("text").asText();

        } catch (Exception e) {
            log.error("Failed to generate stream recap for session {}: {}", session.id(), e.getMessage());
            return null;
        }
    }
}
