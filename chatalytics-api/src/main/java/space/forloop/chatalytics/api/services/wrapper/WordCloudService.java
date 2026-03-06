package space.forloop.chatalytics.api.services.wrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.WordCloud;
import space.forloop.chatalytics.data.repositories.WordCloudRepository;

import java.util.List;

import static space.forloop.chatalytics.api.util.CacheConstants.WORD_CLOUD_SERVICE_FIND_WORD_CLOUD;
import static space.forloop.chatalytics.api.util.CacheGeneratorConstants.SESSION_LIST_KEY_GENERATOR;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordCloudService {

    private final WordCloudRepository wordCloudRepository;

    @Cacheable(value = WORD_CLOUD_SERVICE_FIND_WORD_CLOUD, keyGenerator = SESSION_LIST_KEY_GENERATOR)
    public List<WordCloud> findWordCloud(List<Long> sessionIds, long twitchId, long count) {

        return wordCloudRepository.wordCloud(sessionIds, twitchId, count);
    }
}
