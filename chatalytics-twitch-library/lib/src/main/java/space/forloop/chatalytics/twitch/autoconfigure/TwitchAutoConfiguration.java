package space.forloop.chatalytics.twitch.autoconfigure;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;
import space.forloop.chatalytics.twitch.client.TwitchApiClient;
import space.forloop.chatalytics.twitch.config.TwitchConfig;
import space.forloop.chatalytics.twitch.service.TwitchAuthService;
import space.forloop.chatalytics.twitch.service.TwitchService;
import space.forloop.chatalytics.twitch.service.TwitchServiceImpl;

import java.util.concurrent.ExecutorService;

@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(TwitchProperties.class)
@ConditionalOnProperty(prefix = "twitch", name = {"client-id", "client-secret"})
@Import(TwitchConfig.class)
public class TwitchAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TwitchAuthService twitchAuthService(RestTemplate twitchRestTemplate) {
        return new TwitchAuthService(twitchRestTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    TwitchApiClient twitchApiClient(RestTemplate twitchRestTemplate, TwitchAuthService twitchAuthService) {
        return new TwitchApiClient(twitchRestTemplate, twitchAuthService);
    }

    @Bean
    @ConditionalOnMissingBean
    TwitchService twitchService(TwitchApiClient apiClient, @Qualifier("twitchVirtualExecutor") ExecutorService executor) {
        return new TwitchServiceImpl(apiClient, executor);
    }
}
