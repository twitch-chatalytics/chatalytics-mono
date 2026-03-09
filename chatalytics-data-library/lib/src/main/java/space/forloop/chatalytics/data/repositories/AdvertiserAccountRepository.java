package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.AdvertiserAccount;

import java.util.Optional;

public interface AdvertiserAccountRepository {

    Optional<AdvertiserAccount> findActiveByViewerId(long viewerId);

    Optional<AdvertiserAccount> findByViewerId(long viewerId);
}
