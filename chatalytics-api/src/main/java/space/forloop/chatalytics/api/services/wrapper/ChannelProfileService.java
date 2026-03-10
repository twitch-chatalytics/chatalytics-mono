package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChannelProfile;
import space.forloop.chatalytics.data.repositories.UserRepository;

import static space.forloop.chatalytics.api.util.CacheConstants.CHANNEL_PROFILE;

@Service
@RequiredArgsConstructor
public class ChannelProfileService {

    private final UserRepository userRepository;

    @Cacheable(value = CHANNEL_PROFILE, key = "#channelId")
    public ChannelProfile getProfile(long channelId) {
        return userRepository.findById(channelId)
                .map(u -> new ChannelProfile(u.getId(), u.getLogin(), u.getDisplayName(),
                        u.getBroadcasterType(), u.getDescription(), u.getProfileImageUrl(),
                        u.getOfflineImageUrl(), u.getCreatedAt()))
                .orElse(null);
    }
}
