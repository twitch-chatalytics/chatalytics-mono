package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.ChannelAuthenticity;

import java.util.Optional;

public interface ChannelAuthenticityRepository {

    Optional<ChannelAuthenticity> findByTwitchId(long twitchId);

    void save(ChannelAuthenticity channelAuthenticity);
}
