package space.forloop.chatalytics.twitch.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import space.forloop.chatalytics.twitch.exception.TwitchApiException;
import space.forloop.chatalytics.twitch.service.TwitchAuthService;

import java.net.URI;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwitchApiClient {

    private static final String TWITCH_API_BASE = "https://api.twitch.tv/helix";
    private final RestTemplate twitchRestTemplate;

    private final TwitchAuthService twitchAuthService;
    @Value("${twitch.client-id}")
    private String clientId;

    public <T> T getForObject(String path, Class<T> responseType, MultiValueMap<String, String> queryParams) {
        URI uri = buildUri(path, queryParams);
        HttpHeaders headers = createAuthHeaders();
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        try {
            ResponseEntity<T> response = twitchRestTemplate.exchange(uri, HttpMethod.GET, requestEntity, responseType);
            return Optional.ofNullable(response.getBody())
                    .orElseThrow(() -> new TwitchApiException("Empty response from Twitch"));
        } catch (Exception e) {
            log.error("Error calling Twitch API at {}: {}", uri, e.getMessage());
            throw new TwitchApiException("Failed to call Twitch API", e);
        }
    }

    public URI buildUri(String path, MultiValueMap<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(TWITCH_API_BASE + path);
        if (queryParams != null) {
            builder.queryParams(queryParams);
        }
        return builder.build().toUri();
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Client-ID", clientId);
        headers.setBearerAuth(twitchAuthService.getAccessToken());
        return headers;
    }
}
