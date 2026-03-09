package space.forloop.chatalytics.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import space.forloop.chatalytics.api.services.realtime.LiveMetricsRedisSubscriber;

import java.time.Duration;

import static space.forloop.chatalytics.api.util.CacheConstants.*;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.java());
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            LiveMetricsRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new PatternTopic("live:metrics:*"));
        return container;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration(PUBLIC_STATS, config.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration(CHATTER_PROFILE, config.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(CHATTER_SUMMARY, config.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration(STREAM_RECAP, config.entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration(SESSIONS, config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration(STREAM_CLIPS, config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration(CHANNEL_PROFILE, config.entryTtl(Duration.ofHours(24)))
                .withCacheConfiguration(CHANNEL_DIRECTORY, config.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration(TWITCH_SEARCH, config.entryTtl(Duration.ofSeconds(30)))
                .withCacheConfiguration(PENDING_REQUESTS, config.entryTtl(Duration.ofMinutes(2)))
                .withCacheConfiguration(SESSION_AUTHENTICITY, config.entryTtl(Duration.ofHours(12)))
                .withCacheConfiguration(CHANNEL_AUTHENTICITY, config.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration(SOCIALBLADE_CHANNEL, config.entryTtl(Duration.ofHours(6)))
                .withCacheConfiguration(SOCIALBLADE_DAILY, config.entryTtl(Duration.ofHours(12)))
                .build();
    }
}
