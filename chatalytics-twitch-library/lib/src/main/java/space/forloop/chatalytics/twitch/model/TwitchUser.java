package space.forloop.chatalytics.twitch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.io.Serializable;
import java.time.LocalDateTime;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwitchUser(
        @JsonProperty("id") long id, @JsonProperty("login") String login,
        @JsonProperty("display_name") String displayName, @JsonProperty("type") String type,
        @JsonProperty("broadcaster_type") String broadcasterType,
        @JsonProperty("description") String description,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("offline_image_url") String offlineImageUrl,
        @JsonProperty("view_count") int viewCount,
        @JsonProperty("created_at") LocalDateTime createdAt) implements Serializable {
}