package space.forloop.chatalytics.data.domain;

import java.io.Serializable;
import java.time.LocalDate;

public record SocialBladeDailyPoint(
        long channelId,
        LocalDate date,
        Long followers,
        Long views,
        Integer followerChange,
        Long viewChange
) implements Serializable {}
