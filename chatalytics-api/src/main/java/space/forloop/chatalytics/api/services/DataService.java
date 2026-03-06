package space.forloop.chatalytics.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.api.config.AuthenticationFacade;
import space.forloop.chatalytics.api.domain.SummaryResponse;
import space.forloop.chatalytics.api.domain.TopChatterResponse;
import space.forloop.chatalytics.api.services.wrapper.SessionService;
import space.forloop.chatalytics.api.services.wrapper.SessionSummaryService;
import space.forloop.chatalytics.api.services.wrapper.WordCloudService;
import space.forloop.chatalytics.data.domain.WordCloud;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.generated.tables.pojos.SessionSummary;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataService {

    private final AuthenticationFacade authenticationFacade;

    private final SessionSummaryService sessionSummaryService;

    private final SessionService sessionService;

    private final WordCloudService wordCloudService;

    public List<Session> findSessions() {
        return sessionService.fetchSessions();
    }

    public List<WordCloud> findWordCloud(List<Long> sessionIds) {
        return wordCloudService.findWordCloud(sessionIds, authenticationFacade.getTwitchId(), 1L);
    }

    public SummaryResponse findSessionSummary(List<Long> sessionIds) {
        List<SessionSummary> sessions = sessionSummaryService.findAll(sessionIds, authenticationFacade.getTwitchId());

        double messagesPerMinute = sessions.stream().mapToDouble(SessionSummary::getMessagesPerMinute).average().orElse(0);
        double totalMessages = sessions.stream().mapToDouble(SessionSummary::getTotalMessages).sum();
        double totalChatters = sessions.stream().mapToDouble(SessionSummary::getTotalChatters).sum();
        double totalSessions = sessions.stream().mapToDouble(SessionSummary::getTotalSessions).max().orElse(0);
        double totalMentions = sessions.stream().mapToDouble(SessionSummary::getMentions).max().orElse(0);

        return SummaryResponse.builder()
                .messagesPerMinute(messagesPerMinute)
                .totalMessages(totalMessages)
                .totalChatters(totalChatters)
                .totalSessions(totalSessions)
                .totalMentions(totalMentions)
                .build();
    }

    public TopChatterResponse findTopChatter(List<Long> sessionIds) {
        List<SessionSummary> sessions = sessionSummaryService.findAll(sessionIds, authenticationFacade.getTwitchId());

        Map<String, Long> chatterCounts = new HashMap<>();

        for (SessionSummary session : sessions) {
            String chatter = session.getTopChatterByMessageCount();
            Long value = session.getTopChatterByMessageCountValue();

            chatterCounts.merge(chatter, value, Long::sum);
        }

        var a = chatterCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(new AbstractMap.SimpleEntry<>("", 0L));

        return TopChatterResponse
                .builder()
                .author(a.getKey())
                .count(a.getValue())
                .build();
    }
}
