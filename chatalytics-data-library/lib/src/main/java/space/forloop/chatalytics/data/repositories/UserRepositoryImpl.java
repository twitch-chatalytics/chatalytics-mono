package space.forloop.chatalytics.data.repositories;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import space.forloop.chatalytics.data.generated.tables.pojos.User;

import java.util.List;
import java.util.Optional;

import static space.forloop.chatalytics.data.generated.Tables.SESSION_SUMMARY;
import static space.forloop.chatalytics.data.generated.Tables.USER;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final DSLContext dsl;

    @Override
    public Optional<User> findById(long id) {
        return dsl.selectFrom(USER)
                .where(USER.ID.eq(id))
                .fetchOptionalInto(User.class);
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return dsl.selectFrom(USER)
                .where(USER.LOGIN.eq(login))
                .fetchOptionalInto(User.class);
    }

    @Override
    public List<User> findAll() {
        return dsl.selectFrom(USER)
                .orderBy(USER.LOGIN)
                .fetchInto(User.class);
    }

    @Override
    public List<User> findAllOnline() {
        return dsl.select(USER.asterisk())
                .from(USER)
                .join(SESSION_SUMMARY)
                .on(USER.ID.eq(SESSION_SUMMARY.TWITCH_ID))
                .where(SESSION_SUMMARY.TOTAL_MESSAGES.greaterThan(0L))
                .fetchInto(User.class);
    }

    @Override
    public User save(User user) {
        return dsl.insertInto(USER)
                .set(dsl.newRecord(USER, user))
                .onConflict(USER.ID)
                .doUpdate()
                .set(dsl.newRecord(USER, user))
                .returning()
                .fetchSingleInto(User.class);
    }

    @Override
    public void saveAll(List<User> users) {
        var queries = users.stream()
                .map(user -> dsl.insertInto(USER)
                        .set(dsl.newRecord(USER, user))
                        .onDuplicateKeyIgnore())
                .toList();

        dsl.batch(queries).execute();
    }

    @Override
    public List<Long> findFeaturedIds() {
        return dsl.select(USER.ID)
                .from(USER)
                .where(DSL.field("featured", Boolean.class).isTrue())
                .fetchInto(Long.class);
    }
}
