package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import space.forloop.chatalytics.data.generated.tables.pojos.RollupHistory;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;

import java.util.Optional;

import static space.forloop.chatalytics.data.generated.Tables.ROLLUP_HISTORY;


@Slf4j
@Service
@RequiredArgsConstructor
public class RollupRepositoryImpl implements RollupRepository {

    private final DSLContext dsl;

    public Optional<RollupHistory> findLatest(Session session) {

        return dsl.selectFrom(ROLLUP_HISTORY)
                .where(ROLLUP_HISTORY.SESSION_ID.eq(session.getId()))
                .orderBy(ROLLUP_HISTORY.UPDATED_AT)
                .limit(1)
                .fetchOptionalInto(RollupHistory.class);
    }

    @Override
    public void writePartial(Session session) {

        dsl.insertInto(ROLLUP_HISTORY)
                .set(ROLLUP_HISTORY.SESSION_ID, session.getId())
                .set(ROLLUP_HISTORY.COMPLETE, false)
                .execute();
    }

    @Override
    public void writeFinal(Session session) {

        dsl.insertInto(ROLLUP_HISTORY)
                .set(ROLLUP_HISTORY.SESSION_ID, session.getId())
                .set(ROLLUP_HISTORY.COMPLETE, true)
                .execute();
    }
}
