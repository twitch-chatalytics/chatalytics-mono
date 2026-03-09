package space.forloop.chatalytics.api.services.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import space.forloop.chatalytics.data.domain.Viewer;
import space.forloop.chatalytics.data.repositories.AdvertiserAccountRepository;
import space.forloop.chatalytics.data.repositories.ViewerRepository;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom OAuth2 user service for Twitch. The Twitch helix/users endpoint
 * requires a Client-ID header that standard OAuth2 user-info requests don't
 * include, so we make the call manually and persist the viewer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwitchOAuth2UserService extends DefaultOAuth2UserService {

    private static final String HELIX_USERS = "https://api.twitch.tv/helix/users";

    private final ViewerRepository viewerRepository;
    private final AdvertiserAccountRepository advertiserAccountRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String accessToken = userRequest.getAccessToken().getTokenValue();
        String clientId = userRequest.getClientRegistration().getClientId();

        Map<String, Object> userData = fetchTwitchUser(accessToken, clientId);

        long twitchId = Long.parseLong(String.valueOf(userData.get("id")));
        String login = (String) userData.get("login");
        String displayName = (String) userData.get("display_name");
        String profileImageUrl = (String) userData.get("profile_image_url");

        viewerRepository.save(new Viewer(twitchId, login, displayName, profileImageUrl, Instant.now()));

        Map<String, Object> attributes = new HashMap<>(userData);
        attributes.put("twitchId", twitchId);

        // Grant ROLE_ADVERTISER if active advertiser account exists
        boolean isAdvertiser = advertiserAccountRepository.findActiveByViewerId(twitchId).isPresent();
        var roles = isAdvertiser
                ? AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ADVERTISER")
                : AuthorityUtils.createAuthorityList("ROLE_USER");

        return new DefaultOAuth2User(roles, attributes, "id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchTwitchUser(String accessToken, String clientId) {
        RestTemplate rest = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Client-Id", clientId);

        RequestEntity<Void> request = RequestEntity
                .get(URI.create(HELIX_USERS))
                .headers(h -> h.addAll(headers))
                .build();

        Map<String, Object> body = rest.exchange(request, Map.class).getBody();
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        return data.getFirst();
    }
}
