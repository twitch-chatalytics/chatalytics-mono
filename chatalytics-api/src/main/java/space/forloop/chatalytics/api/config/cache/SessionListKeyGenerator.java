package space.forloop.chatalytics.api.config.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.config.AuthenticationFacade;

import java.lang.reflect.Method;
import java.util.List;

import static space.forloop.chatalytics.api.util.CacheGeneratorConstants.SESSION_LIST_KEY_GENERATOR;

@RequiredArgsConstructor
@Component(SESSION_LIST_KEY_GENERATOR)
public class SessionListKeyGenerator implements KeyGenerator {

    private final AuthenticationFacade authenticationFacade;

    @Override
    public Object generate(Object target, Method method, Object... params) {
        if (params.length > 0 && params[0] instanceof List<?> sessionIds) {

            return authenticationFacade.getTwitchId() + "-" +
                    sessionIds.stream()
                            .sorted()
                            .map(Object::toString)
                            .reduce((a, b) -> a + "_" + b)
                            .orElse("");
        }
        return authenticationFacade.getTwitchId();
    }
}