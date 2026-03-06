package space.forloop.chatalytics.session.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    NewTopic offlineTopic() {
        return TopicBuilder.name("raw-sessions-offline")
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic onlineTopic() {
        return TopicBuilder.name("raw-sessions-online")
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(ConsumerFactory<Object, Object> kafkaConsumerFactory) {
        final ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory);
        factory.setBatchListener(true);
        factory.setConcurrency(Runtime.getRuntime().availableProcessors());

        return factory;
    }
}