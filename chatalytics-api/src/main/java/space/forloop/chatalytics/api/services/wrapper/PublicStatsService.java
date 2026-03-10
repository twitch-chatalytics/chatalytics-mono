package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelStats;
import space.forloop.chatalytics.data.repositories.MessageRepository;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.chatalytics.data.repositories.StreamSnapshotRepository;

import static space.forloop.chatalytics.api.util.CacheConstants.PUBLIC_STATS;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicStatsService {

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final StreamSnapshotRepository snapshotRepository;

    @Cacheable(value = PUBLIC_STATS, key = "#channelId", sync = true)
    public ChannelStats getStats(Long channelId) {

        return new ChannelStats(
                messageRepository.countAllMessages(channelId),
                messageRepository.countDistinctAuthors(channelId),
                messageRepository.topChatters(channelId, 10),
                messageRepository.peakHour(channelId).orElse(null),
                sessionRepository.countByUserId(channelId),
                messageRepository.avgMessagesPerSession(channelId),
                messageRepository.avgChattersPerSession(channelId),
                sessionRepository.avgStreamDurationMinutes(channelId),
                snapshotRepository.topGamesByChannelId(channelId, 10)
        );
    }
}
