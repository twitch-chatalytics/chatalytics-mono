package space.forloop.chatalytics.api.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import space.forloop.chatalytics.api.services.BrandSafetyService;

import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrandSafetyTask {

    private final DSLContext dsl;
    private final BrandSafetyService brandSafetyService;

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void computeBrandSafety() {
        // Find all channels that have sessions but no brand safety record,
        // or whose record is older than 24 hours
        var sessionTable = table(name("chat", "session"));
        var bsTable = table(name("chat", "channel_brand_safety"));

        Result<? extends Record> channels = dsl.selectDistinct(
                        field(name("chat", "session", "channel_id"), Long.class)
                )
                .from(sessionTable)
                .leftJoin(bsTable)
                .on(field(name("chat", "session", "channel_id"))
                        .eq(field(name("chat", "channel_brand_safety", "channel_id"))))
                .where(field(name("chat", "channel_brand_safety", "channel_id")).isNull()
                        .or(field(name("chat", "channel_brand_safety", "updated_at"))
                                .lt(currentTimestamp().minus(inline(1440).cast(Integer.class)))))
                .fetch();

        if (channels.isEmpty()) return;

        log.info("Computing brand safety for {} channels", channels.size());

        for (var row : channels) {
            Long channelId = row.get("channel_id", Long.class);
            if (channelId == null) continue;

            try {
                brandSafetyService.computeAndSave(channelId);
            } catch (Exception e) {
                log.error("Failed to compute brand safety for {}: {}", channelId, e.getMessage());
            }
        }
    }
}
