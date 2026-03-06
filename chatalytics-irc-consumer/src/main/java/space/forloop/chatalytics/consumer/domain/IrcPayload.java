package space.forloop.chatalytics.consumer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import space.forloop.chatalytics.data.domain.SessionWithUser;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IrcPayload {
    private String channel;
    private String nick;
    private String userId;
    private String message;
    private Instant timestamp;
    private SessionWithUser session;
}
