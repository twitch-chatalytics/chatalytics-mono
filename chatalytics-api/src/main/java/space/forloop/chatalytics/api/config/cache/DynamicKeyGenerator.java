package space.forloop.chatalytics.api.config.cache;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

@Component
public class DynamicKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        String baseKey = method.getName();
        if (params.length > 0 && params[0] != null) {
            List<Long> sessionIds = (List<Long>) params[0];
            if (!sessionIds.isEmpty()) {
                return baseKey + ":sessions:" + String.join(",", sessionIds.stream()
                        .map(String::valueOf)
                        .toList());
            }
        }
        return baseKey + ":all";
    }
}