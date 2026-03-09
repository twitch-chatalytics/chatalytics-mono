package space.forloop.chatalytics.api.services.wrapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Collectors;

import static space.forloop.chatalytics.api.util.CacheConstants.STREAM_CLIPS;
import static space.forloop.chatalytics.api.util.CacheConstants.STREAM_RECAP;

@Slf4j
@Service
public class StreamRecapService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final MessageWordRepository messageWordRepository;
    private final StreamSnapshotRepository snapshotRepository;
    private final StreamRecapRepository streamRecapRepository;
    private final TwitchService twitchService;
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
            @Value("${anthropic.api-key:}") String apiKey) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.messageWordRepository = messageWordRepository;
        this.snapshotRepository = snapshotRepository;
        this.streamRecapRepository = streamRecapRepository;
        this.twitchService = twitchService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
    }

    @Cacheable(value = STREAM_RECAP, key = "#sessionId", unless = "#result != null && #result.endTime() == null")
    public StreamRecap generateRecap(long sessionId) {
        // Check persisted recap first (for ended streams)
        var persisted = streamRecapRepository.findBySessionId(sessionId);
        if (persisted.isPresent()) {
            return persisted.get();
        }

        // Fall back to live computation (ongoing streams or not yet persisted)
        return computeRecap(sessionId);
    }

    /**
     * Fetches clips from Twitch for a session, cached for 24h in Redis.
     */
    @Cacheable(value = STREAM_CLIPS, key = "#sessionId + ':' + #limit")
    public List<StreamClip> fetchFreshClips(long sessionId, int limit) {
        var session = sessionRepository.findByIdWithUser(sessionId);
        if (session.isEmpty()) return List.of();
        return fetchTopClips(session.get(), limit);
    }

    /**
     * Computes a full recap from source data. Used by the persistence task
     * and as a fallback for live/ongoing streams.
     */
    public StreamRecap computeRecap(long sessionId) {
        var sessionOpt = sessionRepository.findByIdWithUser(sessionId);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        SessionWithUser session = sessionOpt.get();
        long totalMessages = messageRepository.countMessagesBySessionId(sessionId);
        long totalChatters = messageRepository.countChattersBySessionId(sessionId);
        List<StreamSnapshot> snapshots = snapshotRepository.findBySessionId(sessionId);
        List<ChatActivityBucket> chatActivity = messageRepository.chatActivityBySessionId(sessionId, 5);
        List<TopChatter> topChatters = messageRepository.topChattersBySessionId(sessionId, 10);

        // Velocity metrics
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

        // Message analysis
        MessageAnalysis messageAnalysis = messageRepository.messageAnalysisBySessionId(sessionId);

        // Chatter segmentation
        long newChatterCount = messageRepository.newChatterCountBySessionId(sessionId);
        long returningChatterCount = totalChatters - newChatterCount;

        // Top words
        List<TopWord> topWords = messageWordRepository.topWordsBySessionId(sessionId, 20);

        // Game segments
        List<GameSegment> gameSegments = computeGameSegments(sessionId, snapshots);

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

        // Top clips from Twitch
        List<StreamClip> topClips = fetchTopClips(session, 8);

        String aiSummary = generateAiSummary(session, snapshots, chatActivity, topChatters,
                totalMessages, totalChatters, topWords, gameSegments, chatParticipationRate);

        return new StreamRecap(
                sessionId,
                session.startTime(),
                session.endTime(),
                totalMessages,
                totalChatters,
                snapshots,
                chatActivity,
                topChatters,
                aiSummary,
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
            Double chatParticipationRate) {

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
            // Fallback to raw snapshot transitions
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
        List<Message> sample = messageRepository.findSampleBySessionId(session.id(), 150);
        if (!sample.isEmpty()) {
            context.append("Sample messages from this stream:\n");
            sample.forEach(m -> context.append("  [").append(m.getAuthor()).append("] ").append(m.getMessageText()).append("\n"));
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
