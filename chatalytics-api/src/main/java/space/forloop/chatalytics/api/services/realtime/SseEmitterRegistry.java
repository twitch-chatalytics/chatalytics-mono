package space.forloop.chatalytics.api.services.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterRegistry {

    private final ConcurrentHashMap<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void register(long twitchId, SseEmitter emitter) {
        emitters.computeIfAbsent(twitchId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        log.info("SSE client connected for twitchId={} (total={})", twitchId, countForChannel(twitchId));
    }

    public void unregister(long twitchId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(twitchId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                emitters.remove(twitchId);
            }
        }
        log.info("SSE client disconnected for twitchId={} (remaining={})", twitchId, countForChannel(twitchId));
    }

    public void broadcast(long twitchId, String json) {
        Set<SseEmitter> set = emitters.get(twitchId);
        if (set == null || set.isEmpty()) return;

        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event()
                        .name("metrics")
                        .data(json));
            } catch (IOException | IllegalStateException e) {
                set.remove(emitter);
                log.debug("Removed dead SSE emitter for twitchId={}", twitchId);
            }
        }
    }

    public boolean hasSubscribers(long twitchId) {
        Set<SseEmitter> set = emitters.get(twitchId);
        return set != null && !set.isEmpty();
    }

    public Set<Long> activeChannels() {
        return emitters.keySet();
    }

    private int countForChannel(long twitchId) {
        Set<SseEmitter> set = emitters.get(twitchId);
        return set != null ? set.size() : 0;
    }

    @Scheduled(fixedRate = 30000)
    public void sweepDeadEmitters() {
        for (var entry : emitters.entrySet()) {
            Set<SseEmitter> set = entry.getValue();
            set.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    return false;
                } catch (IOException | IllegalStateException e) {
                    return true;
                }
            });
            if (set.isEmpty()) {
                emitters.remove(entry.getKey());
            }
        }
    }
}
