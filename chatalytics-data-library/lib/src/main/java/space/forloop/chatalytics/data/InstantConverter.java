package space.forloop.chatalytics.data;


import org.jooq.Converter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class InstantConverter implements Converter<OffsetDateTime, Instant> {

    @Override
    public Instant from(OffsetDateTime databaseObject) {
        return (databaseObject == null)
                ? null
                : databaseObject.toInstant();
    }

    @Override
    public OffsetDateTime to(Instant userObject) {
        return (userObject == null)
                ? null
                : OffsetDateTime.ofInstant(userObject, ZoneOffset.UTC);
    }

    @Override
    public Class<OffsetDateTime> fromType() {
        return OffsetDateTime.class;
    }

    @Override
    public Class<Instant> toType() {
        return Instant.class;
    }
}
