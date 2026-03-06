package space.forloop.irc.producer.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }
}
