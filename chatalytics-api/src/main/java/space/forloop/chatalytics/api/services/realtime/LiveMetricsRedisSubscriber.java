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

        // Global counter channel uses 0L sentinel key
        if ("live:global:messages".equals(channel)) {
            sseEmitterRegistry.broadcast(0L, json);
            return;
        }

        // channel format: "live:metrics:{channelId}"
        try {
            String channelIdStr = channel.substring(channel.lastIndexOf(':') + 1);
            long channelId = Long.parseLong(channelIdStr);
            sseEmitterRegistry.broadcast(channelId, json);
        } catch (NumberFormatException e) {
            log.warn("Invalid channelId in Redis channel: {}", channel);
        }
    }
}
