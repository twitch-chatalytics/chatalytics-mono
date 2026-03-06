package space.forloop.chatalytics.data.repositories;

import java.time.Instant;
import java.util.List;

import space.forloop.chatalytics.data.domain.StreamSnapshot;
import space.forloop.chatalytics.data.domain.TopGame;

public interface StreamSnapshotRepository {

    void write(long sessionId, long twitchId, String gameName, String title, int viewerCount);

    List<StreamSnapshot> findBySessionId(long sessionId);

    List<StreamSnapshot> findByTwitchId(long twitchId, Instant from, Instant to);

    List<TopGame> topGamesByTwitchId(long twitchId, int limit);
}
