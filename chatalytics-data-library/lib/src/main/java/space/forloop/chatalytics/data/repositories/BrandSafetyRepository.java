package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.ChannelBrandSafety;

import java.util.Optional;

public interface BrandSafetyRepository {

    Optional<ChannelBrandSafety> findByChannelId(long channelId);

    void save(ChannelBrandSafety safety);
}
