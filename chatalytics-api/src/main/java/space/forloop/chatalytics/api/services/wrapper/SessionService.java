package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.api.config.AuthenticationFacade;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.repositories.SessionRepository;

import java.util.List;

import static space.forloop.chatalytics.api.util.CacheConstants.SESSION_SERVICE_FETCH_SESSION;
import static space.forloop.chatalytics.api.util.CacheGeneratorConstants.TWITCH_ID_KEY_GENERATOR;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final AuthenticationFacade authenticationFacade;

    private final SessionRepository sessionRepository;

    @Cacheable(value = SESSION_SERVICE_FETCH_SESSION, keyGenerator = TWITCH_ID_KEY_GENERATOR)
    public List<Session> fetchSessions() {

        return sessionRepository.findAllByUserId(authenticationFacade.getTwitchId());
    }
}
