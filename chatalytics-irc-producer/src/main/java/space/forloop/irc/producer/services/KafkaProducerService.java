package space.forloop.irc.producer.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.IrcPayload;
import space.forloop.irc.producer.configuration.KafkaConsumerConfig;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, IrcPayload> kafkaTemplate;

    public void sendMessage(IrcPayload ircPayload) {
        kafkaTemplate.send(KafkaConsumerConfig.getTOPIC(), ircPayload);
    }
}
