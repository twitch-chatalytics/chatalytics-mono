package space.forloop.chatalytics.api.services.wrapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.UserRepository;
import space.forloop.chatalytics.twitch.service.TwitchService;

import static space.forloop.chatalytics.api.util.CacheConstants.USER_SERVICE_FIND_BY_LOGIN;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final ObjectMapper objectMapper;

    private final TwitchService twitchService;

    private final UserRepository userRepository;

    @Cacheable(value = USER_SERVICE_FIND_BY_LOGIN)
    public User findOrCreateUser(String login) {
        var userOptional = userRepository.findByLogin(login);

        return userOptional.orElseGet(() -> createNewUser(login));
    }

    private User createNewUser(String twitchUsername) {
        var twitchUser = twitchService.findUserByLogin(twitchUsername);
        var user = objectMapper.convertValue(twitchUser, User.class);

        return userRepository.save(user);
    }
}