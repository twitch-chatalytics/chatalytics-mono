package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.domain.WordCloud;

import java.util.List;

import static org.jooq.impl.DSL.field;
import static space.forloop.chatalytics.data.generated.Tables.MESSAGE_WORD;

@Service
@RequiredArgsConstructor
public class WordCloudRepositoryImpl implements WordCloudRepository {

    private final DSLContext dsl;

    public List<WordCloud> wordCloud(List<Long> sessionIds, Long userId, Long limit) {
        return dsl
                    .select(MESSAGE_WORD.WORD, DSL.count().as("frequency"))
                    .from(MESSAGE_WORD)
                    .where(MESSAGE_WORD.TWITCH_ID.eq(userId))
                .and(sessionIds != null && !sessionIds.isEmpty()
                        ? MESSAGE_WORD.SESSION_ID.in(sessionIds)
                        : DSL.noCondition())
                    .groupBy(MESSAGE_WORD.WORD)
                    .orderBy(field("frequency").desc())
                    .limit(limit)
                    .fetchInto(WordCloud.class);
    }
}
