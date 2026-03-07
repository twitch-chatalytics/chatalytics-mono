package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.services.wrapper.StreamRecapService;
import space.forloop.chatalytics.data.domain.StreamRecap;
import space.forloop.chatalytics.data.repositories.StreamRecapRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamRecapPersistenceTask {

    private final StreamRecapRepository streamRecapRepository;
    private final StreamRecapService streamRecapService;

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void persistCompletedRecaps() {
        List<Long> sessionIds = streamRecapRepository.findSessionIdsWithoutRecap();
        if (sessionIds.isEmpty()) return;

        log.info("Found {} completed sessions without persisted recaps", sessionIds.size());

        for (long sessionId : sessionIds) {
            try {
                StreamRecap recap = streamRecapService.computeRecap(sessionId);
                if (recap != null) {
                    streamRecapRepository.save(recap);
                    log.info("Persisted recap for session {}", sessionId);
                }
            } catch (Exception e) {
                log.error("Failed to persist recap for session {}: {}", sessionId, e.getMessage());
            }
        }
    }
}
