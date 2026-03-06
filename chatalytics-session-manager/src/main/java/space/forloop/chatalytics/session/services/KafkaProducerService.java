package space.forloop.chatalytics.session.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Session> kafkaTemplate;

    public void sendOffline(Session session) {
        kafkaTemplate.send("raw-sessions-offline", session);
    }

    public void sendOnline(Session session) {
        kafkaTemplate.send("raw-sessions-online", session);
    }
}
