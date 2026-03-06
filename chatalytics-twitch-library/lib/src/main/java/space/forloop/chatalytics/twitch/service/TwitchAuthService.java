package space.forloop.chatalytics.twitch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import space.forloop.chatalytics.twitch.exception.TwitchApiException;
import space.forloop.chatalytics.twitch.model.TwitchTokenResponse;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwitchAuthService {

    private static final String TWITCH_TOKEN_URL = "https://id.twitch.tv/oauth2/token";
    private final RestTemplate twitchRestTemplate;
    @Value("${twitch.client-id}")
    private String clientId;
    @Value("${twitch.client-secret}")
    private String clientSecret;

    /**
     * Retrieves and caches the Twitch access token.
     */
    @Cacheable("twitchAccessToken")
    public String getAccessToken() {
        try {
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(createTokenRequestBody(), createHeaders());
            ResponseEntity<TwitchTokenResponse> response = twitchRestTemplate.exchange(
                    TWITCH_TOKEN_URL,
                    HttpMethod.POST,
                    request,
                    TwitchTokenResponse.class
            );

            return Optional.ofNullable(response.getBody())
                    .map(TwitchTokenResponse::getAccessToken)
                    .orElseThrow(() -> new TwitchApiException("Failed to obtain access token"));
        } catch (Exception e) {
            log.error("Error retrieving Twitch access token", e);
            throw new TwitchApiException("Failed to obtain access token", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private MultiValueMap<String, String> createTokenRequestBody() {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", "client_credentials");
        return body;
    }
}
