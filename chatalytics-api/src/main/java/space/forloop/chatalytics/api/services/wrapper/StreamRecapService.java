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
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.repositories.MessageRepository;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.chatalytics.data.repositories.StreamSnapshotRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static space.forloop.chatalytics.api.util.CacheConstants.STREAM_RECAP;

@Slf4j
@Service
public class StreamRecapService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final StreamSnapshotRepository snapshotRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public StreamRecapService(
            SessionRepository sessionRepository,
            MessageRepository messageRepository,
            StreamSnapshotRepository snapshotRepository,
            @Value("${anthropic.api-key:}") String apiKey) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.snapshotRepository = snapshotRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
    }

    @Cacheable(value = STREAM_RECAP, key = "#sessionId")
    public StreamRecap generateRecap(long sessionId) {
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

        String aiSummary = generateAiSummary(session, snapshots, chatActivity, topChatters, totalMessages, totalChatters);

        return new StreamRecap(
                sessionId,
                session.startTime(),
                session.endTime(),
                totalMessages,
                totalChatters,
                snapshots,
                chatActivity,
                topChatters,
                aiSummary
        );
    }

    private String generateAiSummary(
            SessionWithUser session,
            List<StreamSnapshot> snapshots,
            List<ChatActivityBucket> chatActivity,
            List<TopChatter> topChatters,
            long totalMessages,
            long totalChatters) {

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
        context.append("Unique chatters: ").append(totalChatters).append("\n\n");

        // Game/category changes
        if (!snapshots.isEmpty()) {
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
                You are generating a stream recap for a Twitch streamer. Below is data from one streaming session.
                Write a concise, engaging recap (3-5 paragraphs) that tells the story of this stream from the chat's perspective.

                Include:
                - What games/categories were played and how chat reacted to each
                - When the biggest chat moments happened and what drove them
                - Notable viewer engagement patterns (peaks, dips)
                - Who the most active chatters were
                - The overall mood/vibe of the stream

                Write in a casual, informative tone. Use specific numbers where relevant.
                Do not use bullet points or headers — write flowing paragraphs.
                If there are no stream snapshots yet, focus on what you can learn from the chat messages alone.

                Stream data:
                %s""".formatted(context.toString());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> body = Map.of(
                    "model", "claude-haiku-4-5-20251001",
                    "max_tokens", 1024,
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
