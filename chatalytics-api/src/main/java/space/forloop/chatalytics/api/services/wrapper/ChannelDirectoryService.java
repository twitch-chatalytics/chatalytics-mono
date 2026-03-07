package space.forloop.chatalytics.api.services.wrapper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import space.forloop.chatalytics.api.dto.ChannelDtos.TwitchUserResult;
import space.forloop.chatalytics.data.domain.ChannelProfile;
import space.forloop.chatalytics.data.repositories.UserRepository;
import space.forloop.chatalytics.twitch.client.TwitchApiClient;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static space.forloop.chatalytics.api.util.CacheConstants.CHANNEL_DIRECTORY;
import static space.forloop.chatalytics.api.util.CacheConstants.TWITCH_SEARCH;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelDirectoryService {

    private final UserRepository userRepository;
    private final TwitchApiClient twitchApiClient;

    @Cacheable(value = CHANNEL_DIRECTORY, key = "'all'")
    public List<ChannelProfile> listTrackedChannels() {
        return userRepository.findAll().stream()
                .map(u -> new ChannelProfile(
                        u.getId(),
                        u.getLogin(),
                        u.getDisplayName(),
                        u.getBroadcasterType(),
                        u.getDescription(),
                        u.getProfileImageUrl(),
                        u.getOfflineImageUrl(),
                        u.getCreatedAt()))
                .toList();
    }

    @Cacheable(value = TWITCH_SEARCH, key = "#query.toLowerCase()")
    public List<TwitchUserResult> searchTwitch(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
            queryParams.add("query", query);
            queryParams.add("first", "10");

            TwitchSearchResponse response = twitchApiClient.getForObject(
                    "/search/channels", TwitchSearchResponse.class, queryParams);

            if (response == null || response.data() == null) {
                return Collections.emptyList();
            }

            Set<String> trackedLogins = listTrackedChannels().stream()
                    .map(c -> c.login().toLowerCase())
                    .collect(Collectors.toSet());

            return response.data().stream()
                    .map(ch -> new TwitchUserResult(
                            Long.parseLong(ch.id()),
                            ch.broadcasterLogin(),
                            ch.displayName(),
                            ch.thumbnailUrl(),
                            ch.broadcasterType() != null ? ch.broadcasterType() : "",
                            trackedLogins.contains(ch.broadcasterLogin().toLowerCase())))
                    .toList();
        } catch (Exception e) {
            log.error("Error searching Twitch channels for query '{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TwitchSearchResponse(List<TwitchSearchChannel> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TwitchSearchChannel(
            String id,
            @JsonProperty("broadcaster_login") String broadcasterLogin,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("is_live") boolean isLive,
            @JsonProperty("broadcaster_type") String broadcasterType
    ) {}

}
