package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.AlertEvent;
import space.forloop.chatalytics.data.domain.AlertRule;

import java.util.List;

public interface AlertRepository {

    List<AlertRule> findRulesByChannelId(long channelId);

    List<AlertRule> findAllEnabledRules();

    AlertRule saveRule(AlertRule rule);

    void deleteRule(long id);

    List<AlertEvent> findEventsByChannelId(long channelId, int limit);

    List<AlertEvent> findRecentEvents(int limit);

    void saveEvent(AlertEvent event);

    void acknowledgeEvent(long eventId);
}
