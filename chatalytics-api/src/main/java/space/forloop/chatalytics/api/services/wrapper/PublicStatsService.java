package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelStats;
import space.forloop.chatalytics.data.repositories.MessageRepository;

import static space.forloop.chatalytics.api.util.CacheConstants.PUBLIC_STATS;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicStatsService {

    private final MessageRepository messageRepository;

    @Cacheable(value = PUBLIC_STATS, key = "#twitchId")
    public ChannelStats getStats(Long twitchId) {

        return new ChannelStats(
                messageRepository.countAllMessages(twitchId),
                messageRepository.countDistinctAuthors(twitchId),
                messageRepository.topChatters(twitchId, 10),
                messageRepository.peakHour(twitchId).orElse(null)
        );
    }
}
