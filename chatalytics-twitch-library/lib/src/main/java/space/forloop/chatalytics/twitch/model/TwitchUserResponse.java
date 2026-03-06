package space.forloop.chatalytics.twitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TwitchUserResponse(
        @JsonProperty("data")
        List<TwitchUser> data
) {
}
