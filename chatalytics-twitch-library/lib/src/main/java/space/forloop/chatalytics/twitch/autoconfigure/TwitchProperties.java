package space.forloop.chatalytics.twitch.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "twitch")
public class TwitchProperties {

    /**
     * Twitch client ID obtained from Twitch Developer Console
     */
    private String clientId;

    /**
     * Twitch client secret obtained from Twitch Developer Console
     */
    private String clientSecret;

    /**
     * Base URL for Twitch API
     */
    private String apiBaseUrl = "https://api.twitch.tv/helix";

    /**
     * Authentication URL for Twitch API
     */
    private String authUrl = "https://id.twitch.tv/oauth2/token";
}