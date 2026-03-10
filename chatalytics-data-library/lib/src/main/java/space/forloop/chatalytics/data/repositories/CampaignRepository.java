package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.Campaign;

import java.util.List;
import java.util.Optional;

public interface CampaignRepository {

    List<Campaign> findByTwitchId(long twitchId);

    Optional<Campaign> findById(long id);

    Campaign save(Campaign campaign);

    void delete(long id);

    void addSession(long campaignId, long sessionId);

    void removeSession(long campaignId, long sessionId);

    List<Long> findSessionIds(long campaignId);
}
