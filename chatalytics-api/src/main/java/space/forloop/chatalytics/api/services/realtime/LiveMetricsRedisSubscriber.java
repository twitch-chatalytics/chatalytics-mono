package space.forloop.chatalytics.api.services.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveMetricsRedisSubscriber implements MessageListener {

    private final SseEmitterRegistry sseEmitterRegistry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String json = new String(message.getBody());

        // channel format: "live:metrics:{twitchId}"
        try {
            String twitchIdStr = channel.substring(channel.lastIndexOf(':') + 1);
            long twitchId = Long.parseLong(twitchIdStr);
            sseEmitterRegistry.broadcast(twitchId, json);
        } catch (NumberFormatException e) {
            log.warn("Invalid twitchId in Redis channel: {}", channel);
        }
    }
}
