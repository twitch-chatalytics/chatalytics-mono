package space.forloop.chatalytics.api.config.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.config.AuthenticationFacade;

import java.lang.reflect.Method;

import static space.forloop.chatalytics.api.util.CacheGeneratorConstants.TWITCH_ID_KEY_GENERATOR;


@RequiredArgsConstructor
@Component(TWITCH_ID_KEY_GENERATOR)
public class TwitchIdKeyGenerator implements KeyGenerator {

    private final AuthenticationFacade authenticationFacade;

    @Override
    public Object generate(Object target, Method method, Object... params) {
        return authenticationFacade.getTwitchId();
    }
}
