package space.forloop.chatalytics.api.config;

import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

@Service
@RequestScope
public class AuthenticationFacade {

    @Value("${app.mock-enabled}")
    private boolean mockEnabled;

    @Value("${app.mock-user}")
    private Long mockTwitchId;

    @Setter
    private Long overriddenTwitchId;

    public Long getTwitchId() {
        if (overriddenTwitchId != null) {
            return overriddenTwitchId;
        }

        if (mockEnabled) {
            return mockTwitchId;
        }

        return Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}