package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.ChatActivityBucket;
import space.forloop.chatalytics.data.domain.ChatterProfile;
import space.forloop.chatalytics.data.domain.MessageAnalysis;
import space.forloop.chatalytics.data.domain.RepeatedMessage;
import space.forloop.chatalytics.data.domain.TopChatter;
import space.forloop.chatalytics.data.generated.tables.pojos.Message;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository {
    Long countChattersBySessionId(Long sessionId);

    Long countMessagesBySessionId(Long sessionId);

    Long countMentionsBySessionId(Long sessionId, String channelName);

    Optional<TopChatter> topChatterByMessageCount(Long sessionId, List<String> ignoredAuthors);

    List<Message> findByAuthor(String author, Long twitchId, Instant from, Instant to, Instant beforeTimestamp, Long beforeId, int limit);

    List<String> searchAuthors(String query, Long twitchId);

    Optional<Message> findById(Long id);

    List<Message> findContext(Long twitchId, Instant timestamp, int seconds);

    Long countAllMessages(Long twitchId);

    Long countDistinctAuthors(Long twitchId);

    List<TopChatter> topChatters(Long twitchId, int limit);

    Optional<Integer> peakHour(Long twitchId);

    Optional<ChatterProfile> chatterProfile(String author, Long twitchId);

    List<RepeatedMessage> findRepeatedMessages(String author, Long twitchId);

    List<Message> findSampleByAuthor(String author, Long twitchId, int limit);

    List<TopChatter> topChattersBySessionId(Long sessionId, int limit);

    List<ChatActivityBucket> chatActivityBySessionId(Long sessionId, int bucketMinutes);

    List<Message> findSampleBySessionId(Long sessionId, int limit);

    void batchWrite(List<Message> messages);

    MessageAnalysis messageAnalysisBySessionId(Long sessionId);

    long newChatterCountBySessionId(Long sessionId);

    long countMessagesBySessionIdAndTimeRange(Long sessionId, Instant from, Instant to);

    double avgMessagesPerSession(Long twitchId);

    double avgChattersPerSession(Long twitchId);
}
