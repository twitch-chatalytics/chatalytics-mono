package space.forloop.irc.producer.configuration;

import lombok.RequiredArgsConstructor;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.delay.StaticDelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import space.forloop.irc.producer.listeners.OnIrcMessageEvent;

@Configuration
@RequiredArgsConstructor
public class PircBotXConfig {
    private final OnIrcMessageEvent onIrcMessageEvent;
    private final AppProperties appProperties;

    @Bean
    PircBotX pircBotX() {
        final org.pircbotx.Configuration configuration = new org.pircbotx.Configuration.Builder()
                .setName(appProperties.twitchUsername())
                .addServer("irc.chat.twitch.tv", 6697)
                .setSocketFactory(new UtilSSLSocketFactory().trustAllCertificates())
                .setServerPassword("oauth:%s".formatted(appProperties.twitchPassword()))
                .addListener(onIrcMessageEvent)
                .setAutoNickChange(false)
                .setCapEnabled(false)
                .setSnapshotsEnabled(false)
                .setOnJoinWhoEnabled(false)
                .setAutoReconnect(true)
                .setAutoReconnectDelay(new StaticDelay(5000))
                .setVersion("2")
                .buildConfiguration();

        return new PircBotX(configuration);
    }
}
