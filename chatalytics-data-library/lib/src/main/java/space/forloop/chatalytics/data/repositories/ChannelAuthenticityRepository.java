package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.ChannelAuthenticity;

import java.util.Optional;

public interface ChannelAuthenticityRepository {

    Optional<ChannelAuthenticity> findByChannelId(long channelId);

    void save(ChannelAuthenticity channelAuthenticity);
}
