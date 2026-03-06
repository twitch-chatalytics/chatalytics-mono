package space.forloop.chatalytics.twitch.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TwitchTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
}
