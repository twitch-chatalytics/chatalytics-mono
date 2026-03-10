package space.forloop.chatalytics.api.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import space.forloop.chatalytics.api.services.realtime.SseEmitterRegistry;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/public/live")
public class LiveMetricsController {

    private static final long SSE_TIMEOUT = 24 * 60 * 60 * 1000L; // 24 hours

    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping(value = "/global/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter globalStream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseEmitterRegistry.register(0L, emitter);
        Runnable cleanup = () -> sseEmitterRegistry.unregister(0L, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    @GetMapping(value = "/{twitchId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable long twitchId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        sseEmitterRegistry.register(twitchId, emitter);

        Runnable cleanup = () -> sseEmitterRegistry.unregister(twitchId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }
}
