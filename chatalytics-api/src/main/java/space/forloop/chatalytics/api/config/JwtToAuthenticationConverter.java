package space.forloop.chatalytics.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import space.forloop.chatalytics.api.services.wrapper.UserService;
import space.forloop.chatalytics.data.generated.tables.pojos.User;

@RequiredArgsConstructor
public class JwtToAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Value("${app.mock-enabled}")
    private boolean mockEnabled;

    @Value("${app.mock-username}")
    private String mockTwitchUsername;

    private final UserService userService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String twitchUsername = mockEnabled ? mockTwitchUsername : jwt.getClaimAsString("twitch_username");

        if (twitchUsername != null) {
            return findOrCreateUser(jwt, twitchUsername);
        }

        return new JwtAuthenticationToken(jwt);
    }

    private AbstractAuthenticationToken findOrCreateUser(Jwt jwt, String twitchUsername) {
        User user = userService.findOrCreateUser(twitchUsername);

        return new JwtAuthenticationToken(jwt, null, String.valueOf(user.getId()));
    }
}