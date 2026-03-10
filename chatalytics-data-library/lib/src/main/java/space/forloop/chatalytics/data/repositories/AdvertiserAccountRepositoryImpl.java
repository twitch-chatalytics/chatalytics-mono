package space.forloop.chatalytics.data.repositories;

import org.jooq.DSLContext;
import space.forloop.chatalytics.data.domain.AdvertiserAccount;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.jooq.impl.DSL.*;

public class AdvertiserAccountRepositoryImpl implements AdvertiserAccountRepository {

    private final DSLContext dsl;

    public AdvertiserAccountRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    private static final org.jooq.Table<?> TABLE = table(name("chat", "advertiser_account"));

    @Override
    public Optional<AdvertiserAccount> findActiveByViewerId(long viewerId) {
        return dsl.selectFrom(TABLE)
                .where(field("viewer_id").eq(viewerId))
                .and(field("status").eq("active"))
                .and(
                        field("expires_at").isNull()
                                .or(field("expires_at").greaterThan(LocalDateTime.now(ZoneOffset.UTC)))
                )
                .fetchOptional()
                .map(r -> new AdvertiserAccount(
                        r.get("id", Long.class),
                        r.get("viewer_id", Long.class),
                        r.get("tier", String.class),
                        r.get("status", String.class),
                        toInstant(r.get("created_at", LocalDateTime.class)),
                        toInstant(r.get("expires_at", LocalDateTime.class)),
                        toInstant(r.get("updated_at", LocalDateTime.class))
                ));
    }

    @Override
    public Optional<AdvertiserAccount> findByViewerId(long viewerId) {
        return dsl.selectFrom(TABLE)
                .where(field("viewer_id").eq(viewerId))
                .fetchOptional()
                .map(r -> new AdvertiserAccount(
                        r.get("id", Long.class),
                        r.get("viewer_id", Long.class),
                        r.get("tier", String.class),
                        r.get("status", String.class),
                        toInstant(r.get("created_at", LocalDateTime.class)),
                        toInstant(r.get("expires_at", LocalDateTime.class)),
                        toInstant(r.get("updated_at", LocalDateTime.class))
                ));
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
