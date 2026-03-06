package space.forloop.chatalytics.api.domain;

import lombok.Builder;
import org.springframework.context.annotation.Bean;

@Builder
public record TopChatterResponse(String author, Long count) {
}
