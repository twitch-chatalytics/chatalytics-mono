package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.SessionMetrics;
import space.forloop.chatalytics.data.generated.tables.pojos.Session;
import space.forloop.chatalytics.data.generated.tables.pojos.SessionSummary;

import java.util.List;

import static space.forloop.chatalytics.data.generated.Tables.SESSION_SUMMARY;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSummaryRepositoryImpl implements SessionSummaryRepository {

    private final DSLContext dsl;

    @Override
    public List<SessionSummary> findAllSessions(List<Long> sessionIds, long twitchId) {
        return dsl.selectFrom(SESSION_SUMMARY)
                .where(SESSION_SUMMARY.TWITCH_ID.eq(twitchId))
                .and(sessionIds != null && !sessionIds.isEmpty()
                        ? SESSION_SUMMARY.SESSION_ID.in(sessionIds)
                        : DSL.noCondition())
                .fetchInto(SessionSummary.class);
    }

    @Override
    @Transactional
    public void write(Session session, SessionMetrics metrics, boolean partial) {

        dsl.insertInto(SESSION_SUMMARY)
                .set(SESSION_SUMMARY.SESSION_ID, session.getId())
                .set(SESSION_SUMMARY.TWITCH_ID, session.getTwitchId())
                .set(SESSION_SUMMARY.MESSAGES_PER_MINUTE, metrics.messagesPerMinute())
                .set(SESSION_SUMMARY.TOTAL_CHATTERS, metrics.totalChatters())
                .set(SESSION_SUMMARY.TOTAL_MESSAGES, metrics.totalMessages())
                .set(SESSION_SUMMARY.TOTAL_SESSIONS, metrics.totalSessions())
                .set(SESSION_SUMMARY.MENTIONS, metrics.totalMentions())
                .set(SESSION_SUMMARY.TOP_CHATTER_BY_MESSAGE_COUNT, metrics.topChatterByMessageCount())
                .set(SESSION_SUMMARY.TOP_CHATTER_BY_MESSAGE_COUNT_VALUE, metrics.topChatterByMessageCountValue())
                .set(SESSION_SUMMARY.IS_PARTIAL, partial)
                .execute();
    }
}
