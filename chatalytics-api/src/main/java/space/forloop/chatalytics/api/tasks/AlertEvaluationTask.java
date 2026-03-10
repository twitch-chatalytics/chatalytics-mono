package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.services.AlertEvaluationService;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEvaluationTask {

    private final AlertEvaluationService alertEvaluationService;

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    public void evaluateAlerts() {
        log.info("Running alert evaluation task");
        try {
            alertEvaluationService.evaluateAlerts();
        } catch (Exception e) {
            log.error("Alert evaluation task failed: {}", e.getMessage());
        }
    }
}
