package space.forloop.chatalytics.chatalyticsdataroller.tasks;

import com.google.common.collect.Lists;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import space.forloop.chatalytics.data.domain.SessionMetrics;
import space.forloop.chatalytics.data.domain.TopChatter;
import space.forloop.chatalytics.data.generated.tables.pojos.RollupHistory;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static space.forloop.chatalytics.chatalyticsdataroller.utils.Constants.IGNORED_USERS;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRollUpTask {

    private final MessageRepository messageRepository;

    private final SessionSummaryRepository sessionSummaryRepository;

    private final SessionRepository sessionRepository;

    private final RollupRepository rollupRepository;

    private final UserRepository userRepository;

    private final TransactionTemplate transactionTemplate;

    private final ExecutorService executorService;

    private final Lock taskLock = new ReentrantLock();

    @Value("${app.dry-run}")
    private boolean isDryRun;

    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void run() {
        if (!taskLock.tryLock()) {
            log.info("Previous task still running, skipping");
            return;
        }
        try {
            processUnrolledSessions();
        } finally {
            taskLock.unlock();
        }
    }

    private void processUnrolledSessions() {
        List<Session> activeSessions = sessionRepository.findUnprocessedActiveSessions();
        List<Session> completedSessions = sessionRepository.findUnprocessedCompletedSessions();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        Lists.partition(activeSessions, 100).forEach(batch ->
                futures.add(CompletableFuture.runAsync(
                        () -> processBatch(batch, true),
                        executorService
                ))
        );

        Lists.partition(completedSessions, 100).forEach(batch ->
                futures.add(CompletableFuture.runAsync(
                        () -> processBatch(batch, false),
                        executorService
                ))
        );

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Error processing sessions", e);
        }
    }

    private void processBatch(List<Session> sessions, boolean isPartial) {
        sessions.forEach(session ->
                transactionTemplate.execute(status -> {
                    try {
                        processSession(session, isPartial);
                        return null;
                    } catch (Exception e) {
                        log.error("Error processing session {}", session.getId(), e);
                        status.setRollbackOnly();
                        return null;
                    }
                })
        );
    }

    private void processSession(Session session, boolean isPartial) {
        var rollup = rollupRepository.findLatest(session);
        var metrics = gatherMetrics(session, rollup.map(RollupHistory::getUpdatedAt).orElse(session.getStartTime()));

        if (isDryRun) {
            log.info("Dry run enabled, not writing.");
            return;
        }

        if (isPartial) {
            sessionSummaryRepository.write(session, metrics, true);
            rollupRepository.writePartial(session);
        } else {
            sessionSummaryRepository.write(session, metrics, false);
            rollupRepository.writeFinal(session);
        }
    }

    private SessionMetrics gatherMetrics(Session session, Instant startTime) {
        log.info("Metric collection for {}", session.getId());

        long totalMessages = messageRepository.countMessagesBySessionId(session.getId());
        long totalChatters = messageRepository.countChattersBySessionId(session.getId());
        long totalMentions = messageRepository.countMentionsBySessionId(session.getId(), getChannelName(session.getTwitchId()).getLogin());
        long totalSessions = sessionRepository.countByUserId(session.getTwitchId());

        Optional<TopChatter> topChatter = messageRepository.topChatterByMessageCount(session.getId(), IGNORED_USERS);

        // Calculate messages per minute using session start/end time
        var duration = Duration.between(session.getStartTime(), session.getEndTime() != null ? session.getEndTime() : Instant.now());
        long messagesPerMinute = duration.toMinutes() > 0 ? totalMessages / duration.toMinutes() : 0;

        String author = "";
        int messageCount = 0;

        if (topChatter.isPresent()) {
            author = topChatter.get().author();
            messageCount = topChatter.get().messageCount();
        }

        return new SessionMetrics(
                messagesPerMinute,
                totalChatters,
                totalMessages,
                totalSessions,
                totalMentions,
                author,
                messageCount
        );
    }

    private User getChannelName(Long twitchId) {
        return userRepository.findById(twitchId).orElseThrow();
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}