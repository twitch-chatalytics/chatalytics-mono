package space.forloop.chatalytics.data.repositories;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.transaction.annotation.Transactional;
import space.forloop.chatalytics.data.domain.Campaign;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

@Slf4j
public class CampaignRepositoryImpl implements CampaignRepository {

    private final DSLContext dsl;

    private static final org.jooq.Table<?> TABLE = table(name("twitch", "campaign"));
    private static final org.jooq.Table<?> SESSION_TABLE = table(name("twitch", "campaign_session"));

    public CampaignRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Campaign> findByTwitchId(long twitchId) {
        return dsl.selectFrom(TABLE)
                .where(field("twitch_id").eq(twitchId))
                .orderBy(field("created_at").desc())
                .fetch()
                .map(this::toCampaign);
    }

    @Override
    public Optional<Campaign> findById(long id) {
        return dsl.selectFrom(TABLE)
                .where(field("id").eq(id))
                .fetchOptional()
                .map(this::toCampaign);
    }

    @Override
    @Transactional
    public Campaign save(Campaign campaign) {
        if (campaign.id() != null) {
            dsl.update(TABLE)
                    .set(field("campaign_name"), campaign.campaignName())
                    .set(field("brand_name"), campaign.brandName())
                    .set(field("brand_keywords"), toArray(campaign.brandKeywords()))
                    .set(field("start_date"), campaign.startDate())
                    .set(field("end_date"), campaign.endDate())
                    .set(field("deal_price"), campaign.dealPrice())
                    .where(field("id").eq(campaign.id()))
                    .execute();
            return campaign;
        }

        Record result = dsl.insertInto(TABLE)
                .set(field("twitch_id"), campaign.twitchId())
                .set(field("campaign_name"), campaign.campaignName())
                .set(field("brand_name"), campaign.brandName())
                .set(field("brand_keywords"), toArray(campaign.brandKeywords()))
                .set(field("start_date"), campaign.startDate())
                .set(field("end_date"), campaign.endDate())
                .set(field("deal_price"), campaign.dealPrice())
                .returning()
                .fetchOne();

        return result != null ? toCampaign(result) : campaign;
    }

    @Override
    @Transactional
    public void delete(long id) {
        dsl.deleteFrom(TABLE)
                .where(field("id").eq(id))
                .execute();
    }

    @Override
    @Transactional
    public void addSession(long campaignId, long sessionId) {
        dsl.insertInto(SESSION_TABLE)
                .set(field("campaign_id"), campaignId)
                .set(field("session_id"), sessionId)
                .onConflict(field("campaign_id"), field("session_id"))
                .doNothing()
                .execute();
    }

    @Override
    @Transactional
    public void removeSession(long campaignId, long sessionId) {
        dsl.deleteFrom(SESSION_TABLE)
                .where(field("campaign_id").eq(campaignId))
                .and(field("session_id").eq(sessionId))
                .execute();
    }

    @Override
    public List<Long> findSessionIds(long campaignId) {
        return dsl.select(field("session_id", Long.class))
                .from(SESSION_TABLE)
                .where(field("campaign_id").eq(campaignId))
                .fetchInto(Long.class);
    }

    private Campaign toCampaign(Record r) {
        String[] keywords = r.get("brand_keywords", String[].class);
        return new Campaign(
                r.get("id", Long.class),
                r.get("twitch_id", Long.class),
                r.get("campaign_name", String.class),
                r.get("brand_name", String.class),
                keywords != null ? Arrays.asList(keywords) : List.of(),
                r.get("start_date", LocalDate.class),
                r.get("end_date", LocalDate.class),
                r.get("deal_price", Double.class),
                toInstant(r.get("created_at", LocalDateTime.class))
        );
    }

    private String[] toArray(List<String> list) {
        if (list == null || list.isEmpty()) return new String[0];
        return list.toArray(new String[0]);
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
