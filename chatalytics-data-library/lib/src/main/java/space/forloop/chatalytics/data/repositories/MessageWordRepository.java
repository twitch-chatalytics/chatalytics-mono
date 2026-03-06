package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.TopWord;

import java.util.List;

public interface MessageWordRepository {
    List<TopWord> topWordsBySessionId(Long sessionId, int limit);
}
