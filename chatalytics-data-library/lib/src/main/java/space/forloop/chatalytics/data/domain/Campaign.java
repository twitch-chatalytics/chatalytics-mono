package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Campaign(
        Long id,
        long channelId,
        String campaignName,
        String brandName,
        List<String> brandKeywords,
        LocalDate startDate,
        LocalDate endDate,
        Double dealPrice,
        Instant createdAt
) implements Serializable {}
