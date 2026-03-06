package space.forloop.chatalytics.twitch.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import space.forloop.chatalytics.twitch.exception.TwitchResponseErrorHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class TwitchConfig {

    @Bean
    ExecutorService twitchVirtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    RestTemplate twitchRestTemplate() {
        return new RestTemplateBuilder()
                .errorHandler(new TwitchResponseErrorHandler())
                .build();
    }

}
