package space.forloop.chatalytics.data.repositories;

import space.forloop.chatalytics.data.domain.WordCloud;

import java.util.List;

public interface WordCloudRepository {

    List<WordCloud> wordCloud(List<Long> sessionIds, Long userId, Long limit);
}
