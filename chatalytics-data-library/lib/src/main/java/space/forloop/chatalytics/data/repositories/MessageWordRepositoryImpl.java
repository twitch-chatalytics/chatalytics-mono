package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import space.forloop.chatalytics.data.domain.TopWord;

import java.util.List;

import static space.forloop.chatalytics.data.generated.tables.MessageWord.MESSAGE_WORD;

@RequiredArgsConstructor
public class MessageWordRepositoryImpl implements MessageWordRepository {

    private final DSLContext dsl;

    @Override
    public List<TopWord> topWordsBySessionId(Long sessionId, int limit) {
        return dsl.select(MESSAGE_WORD.WORD, DSL.count().as("count"))
                .from(MESSAGE_WORD)
                .where(MESSAGE_WORD.SESSION_ID.eq(sessionId))
                .groupBy(MESSAGE_WORD.WORD)
                .orderBy(DSL.count().desc())
                .limit(limit)
                .fetchInto(TopWord.class);
    }
}
