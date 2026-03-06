package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.ChatterProfile;
import space.forloop.chatalytics.data.repositories.MessageRepository;

import static space.forloop.chatalytics.api.util.CacheConstants.CHATTER_PROFILE;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicChatterProfileService {

    private final MessageRepository messageRepository;

    @Cacheable(value = CHATTER_PROFILE, key = "#twitchId + ':' + #author.toLowerCase()")
    public ChatterProfile getChatterProfile(String author, Long twitchId) {
        return messageRepository.chatterProfile(author, twitchId).orElse(null);
    }
}
