package space.forloop.chatalytics.session.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.generated.tables.pojos.User;
import space.forloop.chatalytics.data.repositories.SessionRepository;
import space.forloop.chatalytics.data.repositories.StreamSnapshotRepository;
import space.forloop.chatalytics.twitch.model.StreamData;
import space.forloop.chatalytics.twitch.model.TwitchUser;
import space.forloop.chatalytics.twitch.service.TwitchService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;

    private final StreamSnapshotRepository streamSnapshotRepository;

    private final TwitchService twitchService;

    private final ObjectMapper objectMapper;

    private final KafkaProducerService kafkaProducerService;

    public void updateOnlineSessions(List<User> users) {
        Map<Long, Session> openSessions = sessionRepository
                .findAllOpenSessions()
                .stream()
                .collect(Collectors.toMap(Session::getChannelId, s -> s));

        List<TwitchUser> twitchUsers = users.stream().map(user -> objectMapper.convertValue(user, TwitchUser.class)).toList();

        Set<StreamData> onlineStreams = twitchService.findAllOnlineStreams(twitchUsers);
        Map<String, StreamData> streamByLogin = onlineStreams.stream()
                .collect(Collectors.toMap(StreamData::getUserLogin, s -> s, (a, b) -> a));

        int createdCount = 0;
        int closedCount = 0;
        int snapshotCount = 0;

        for (User user : users) {
            StreamData stream = streamByLogin.get(user.getLogin());
            boolean isOnline = stream != null;
            Session openSession = openSessions.get(user.getId());

            if (isOnline && openSession == null) {
                Session created = createSession(user);
                writeSnapshot(created.getId(), user.getId(), stream);
                createdCount++;
                snapshotCount++;
            } else if (isOnline && openSession != null) {
                writeSnapshot(openSession.getId(), user.getId(), stream);
                snapshotCount++;
            } else if (!isOnline && openSession != null) {
                closeSession(openSession.getId());
                closedCount++;
            }
        }

        log.info("Processed {} users, {} online, created {} sessions, closed {} sessions, {} snapshots",
                users.size(), onlineStreams.size(), createdCount, closedCount, snapshotCount);
    }

    private void writeSnapshot(long sessionId, long channelId, StreamData stream) {
        try {
            streamSnapshotRepository.write(
                    sessionId,
                    channelId,
                    stream.getGameName(),
                    stream.getTitle(),
                    stream.getViewerCount()
            );
        } catch (Exception e) {
            log.warn("Failed to write stream snapshot for session {}: {}", sessionId, e.getMessage());
        }
    }

    private Session createSession(User user) {
        Session session = new Session();
        session.setChannelId(user.getId());
        session.setStartTime(Instant.now());

        Session openSession = sessionRepository.write(session);

        kafkaProducerService.sendOnline(openSession);

        log.info("Created new session for user {}", user.getLogin());
        return openSession;
    }

    private void closeSession(Long sessionId) {
        Session closeSession = sessionRepository.updateSessionEndTime(sessionId, Instant.now());

        kafkaProducerService.sendOffline(closeSession);

        log.info("Closed session with ID {}", sessionId);
    }
}
