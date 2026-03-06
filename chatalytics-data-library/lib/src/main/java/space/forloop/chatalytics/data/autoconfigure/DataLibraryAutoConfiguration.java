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
    WordCloudRepository metricRepository(DSLContext dslContext) {
        return new WordCloudRepositoryImpl(dslContext);
    }

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
    SessionSummaryRepository summaryRepository(DSLContext dslContext) {
        return new SessionSummaryRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    RollupRepository rollupRepository(DSLContext dslContext) {
        return new RollupRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    StreamSnapshotRepository streamSnapshotRepository(DSLContext dslContext) {
        return new StreamSnapshotRepositoryImpl(dslContext);
    }
}
