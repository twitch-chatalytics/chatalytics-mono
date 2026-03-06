package space.forloop.chatalytics.data.repositories;

import java.time.Instant;
import java.util.List;

import space.forloop.chatalytics.data.domain.StreamSnapshot;

public interface StreamSnapshotRepository {

    void write(long sessionId, long twitchId, String gameName, String title, int viewerCount);

    List<StreamSnapshot> findBySessionId(long sessionId);

    List<StreamSnapshot> findByTwitchId(long twitchId, Instant from, Instant to);
}
