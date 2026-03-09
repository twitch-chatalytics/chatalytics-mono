package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.SocialBladeChannel;
import space.forloop.chatalytics.data.domain.SocialBladeDailyPoint;

import java.util.List;
import java.util.Optional;

public interface SocialBladeRepository {

    Optional<SocialBladeChannel> findByTwitchId(long twitchId);

    void save(SocialBladeChannel channel);

    void saveDailyPoints(long twitchId, List<SocialBladeDailyPoint> points);

    List<SocialBladeDailyPoint> findDailyByTwitchId(long twitchId, int limit);

    List<Long> findStaleChannelIds(int staleHours);
}
