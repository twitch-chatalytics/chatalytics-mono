package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.SocialBladeChannel;
import space.forloop.chatalytics.data.domain.SocialBladeDailyPoint;

import java.util.List;
import java.util.Optional;

public interface SocialBladeRepository {

    Optional<SocialBladeChannel> findByChannelId(long channelId);

    void save(SocialBladeChannel channel);

    void saveDailyPoints(long channelId, List<SocialBladeDailyPoint> points);

    List<SocialBladeDailyPoint> findDailyByChannelId(long channelId, int limit);

    List<Long> findStaleChannelIds(int staleHours);
}
