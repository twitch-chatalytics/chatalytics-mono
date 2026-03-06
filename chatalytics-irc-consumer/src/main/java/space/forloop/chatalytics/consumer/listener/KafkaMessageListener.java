package space.forloop.chatalytics.consumer.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.consumer.domain.IrcPayload;
import space.forloop.chatalytics.consumer.service.MessageService;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

    private final ObjectMapper objectMapper;

    private final MessageService messageService;

    @KafkaListener(
            topics = "raw-messages",
            groupId = "2",
            batch = "true",
            containerFactory = "batchFactory"
    )
    public void listener(List<String> records) {
        Thread.ofVirtual().start(() -> {
            try {
                List<IrcPayload> payloads = records.stream()
                        .map(this::parsePayload)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                messageService.processMessages(payloads);
            } catch (Exception e) {
                log.error("Error processing batch", e);
            }
        });
    }

    private IrcPayload parsePayload(String record) {
        try {
            return objectMapper.readValue(record, IrcPayload.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse message: {}", record, e);
            return null;
        }
    }
}