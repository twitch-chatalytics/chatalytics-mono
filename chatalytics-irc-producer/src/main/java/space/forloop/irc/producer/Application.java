package space.forloop.irc.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableAsync
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
