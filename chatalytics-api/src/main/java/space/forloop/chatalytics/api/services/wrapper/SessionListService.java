package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.SessionSummaryView;
import space.forloop.chatalytics.data.repositories.SessionRepository;

import java.util.List;

import static space.forloop.chatalytics.api.util.CacheConstants.SESSIONS;

@Service
@RequiredArgsConstructor
public class SessionListService {

    private final SessionRepository sessionRepository;

    @Cacheable(value = SESSIONS, key = "#twitchId + ':' + #limit")
    public List<SessionSummaryView> findFirstPage(long twitchId, int limit) {
        return sessionRepository.findSessionsWithStats(twitchId, limit);
    }
}
