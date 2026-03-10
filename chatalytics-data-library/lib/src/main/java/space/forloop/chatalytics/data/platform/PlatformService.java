package space.forloop.chatalytics.data.platform;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PlatformService {

    String platform();

    Set<PlatformStreamInfo> findOnlineStreams(List<PlatformUser> users);

    Optional<PlatformUser> findUserByIdentifier(String identifier);

    default List<PlatformClip> findClips(String channelId, Instant from, Instant to, int limit) {
        return List.of();
    }
}
