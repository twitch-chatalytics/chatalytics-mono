package space.forloop.chatalytics.data.autoconfigure;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import space.forloop.chatalytics.data.repositories.*;

@AutoConfiguration
@RequiredArgsConstructor
@Import(JooqConfig.class)
public class DataLibraryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    UserRepository userRepository(DSLContext dslContext) {
        return new UserRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    SessionRepository sessionRepository(DSLContext dslContext) {
        return new SessionRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    MessageRepository messageRepository(DSLContext dslContext) {
        return new MessageRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    StreamSnapshotRepository streamSnapshotRepository(DSLContext dslContext) {
        return new StreamSnapshotRepositoryImpl(dslContext);
    }
}
