package space.forloop.chatalytics.api.services.wrapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import space.forloop.chatalytics.data.generated.tables.pojos.Message;
import space.forloop.chatalytics.data.repositories.MessageRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static space.forloop.chatalytics.api.util.CacheConstants.CHATTER_SUMMARY;

@Slf4j
@Service
public class ChatterSummaryService {

    private final MessageRepository messageRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ChatterSummaryService(
            MessageRepository messageRepository,
            @Value("${anthropic.api-key:}") String apiKey) {
        this.messageRepository = messageRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
    }

    @Cacheable(value = CHATTER_SUMMARY, key = "#twitchId + ':' + #author.toLowerCase()")
    public String summarize(String author, Long twitchId) {

        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        List<Message> sample = messageRepository.findSampleByAuthor(author, twitchId, 200);

        if (sample.isEmpty()) {
            return null;
        }

        String messagesText = sample.stream()
                .map(m -> m.getTimestamp() + " " + m.getMessageText())
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are analyzing the chat history of a Twitch viewer named "%s". \
                Below is a sample of %d messages from their history. \
                Write a brief 2-3 sentence profile of this chatter. \
                Focus on: what topics they discuss, their general tone/attitude, \
                whether they seem like a regular or casual viewer, and any notable patterns. \
                Be direct and observational, not judgmental. Do not use bullet points.

                Messages:
                %s""".formatted(author, sample.size(), messagesText);

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
            log.error("Failed to generate chatter summary for {}: {}", author, e.getMessage());
            return null;
        }
    }
}
