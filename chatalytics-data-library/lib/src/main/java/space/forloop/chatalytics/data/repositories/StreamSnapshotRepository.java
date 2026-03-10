package space.forloop.chatalytics.data.repositories;

import java.time.Instant;
import java.util.List;

import space.forloop.chatalytics.data.domain.StreamSnapshot;
import space.forloop.chatalytics.data.domain.TopGame;

public interface StreamSnapshotRepository {

    void write(long sessionId, long channelId, String gameName, String title, int viewerCount);

    List<StreamSnapshot> findBySessionId(long sessionId);

    List<StreamSnapshot> findByChannelId(long channelId, Instant from, Instant to);

    List<TopGame> topGamesByChannelId(long channelId, int limit);
}
