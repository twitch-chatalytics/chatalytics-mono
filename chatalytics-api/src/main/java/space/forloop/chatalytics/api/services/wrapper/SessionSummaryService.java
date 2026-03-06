package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.generated.tables.pojos.SessionSummary;
import space.forloop.chatalytics.data.repositories.SessionSummaryRepository;

import java.util.List;

import static space.forloop.chatalytics.api.util.CacheConstants.SESSION_SUMMARY_SERVICE_FIND_ALL;
import static space.forloop.chatalytics.api.util.CacheGeneratorConstants.SESSION_LIST_KEY_GENERATOR;

@Slf4j
@Service
@RequiredArgsConstructor

public class SessionSummaryService {

    private final SessionSummaryRepository sessionSummaryRepository;

    @Cacheable(value = SESSION_SUMMARY_SERVICE_FIND_ALL, keyGenerator = SESSION_LIST_KEY_GENERATOR)
    public List<SessionSummary> findAll(List<Long> sessionIds, long twitchId) {

        return sessionSummaryRepository.findAllSessions(sessionIds, twitchId);
    }
}
