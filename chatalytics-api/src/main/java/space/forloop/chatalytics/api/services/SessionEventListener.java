package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import static space.forloop.chatalytics.api.util.CacheConstants.SESSIONS;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionEventListener {

    @CacheEvict(value = SESSIONS, allEntries = true)
    @KafkaListener(topics = "raw-sessions-online", groupId = "chatalytics-api-cache")
    public void onSessionOnline(String payload) {
        log.info("Session started — evicting sessions cache");
    }

    @CacheEvict(value = SESSIONS, allEntries = true)
    @KafkaListener(topics = "raw-sessions-offline", groupId = "chatalytics-api-cache")
    public void onSessionOffline(String payload) {
        log.info("Session ended — evicting sessions cache");
    }
}
