package space.forloop.irc.producer.configuration;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.properties")
public record AppProperties(
        String twitchUsername,
        String twitchPassword,
        String ircManagerHost
) {
}