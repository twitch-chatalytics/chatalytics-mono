package space.forloop.irc.producer.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.SessionWithUser;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.irc.producer.services.ChannelService;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaMessageListener {

    private final SessionRepository sessionRepository;

    private final ChannelService channelService;

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "raw-sessions-online", groupId = "2")
    public void onlineMessage(String message) {
        try {
            log.info("onlineMessage: {}", message);

            Session session = objectMapper.readValue(message, Session.class);
            Optional<SessionWithUser> optionalSessionWithUser = sessionRepository.findByIdWithUser(session.getId());

            if (optionalSessionWithUser.isPresent()) {
                channelService.joinChannel(optionalSessionWithUser.get());
            } else {
                log.warn("Unable to find session with id {}", session.getId());
            }
        } catch (Exception e) {
            log.error("Failed to process online session event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "raw-sessions-offline", groupId = "2")
    public void offlineMessage(String message) {
        try {
            log.info("offlineMessage: {}", message);

            Session session = objectMapper.readValue(message, Session.class);
            Optional<SessionWithUser> optionalSessionWithUser = sessionRepository.findByIdWithUser(session.getId());

            if (optionalSessionWithUser.isPresent()) {
                channelService.leaveChannel(optionalSessionWithUser.get().login());
            } else {
                log.warn("Unable to find session with id {}", session.getId());
            }
        } catch (Exception e) {
            log.error("Failed to process offline session event: {}", e.getMessage());
        }
    }
}
