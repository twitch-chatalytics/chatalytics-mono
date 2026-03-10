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

    @Bean("chatSessionRepository")
    @ConditionalOnMissingBean(SessionRepository.class)
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

    @Bean
    @ConditionalOnMissingBean
    MessageWordRepository messageWordRepository(DSLContext dslContext) {
        return new MessageWordRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    StreamRecapRepository streamRecapRepository(DSLContext dslContext) {
        return new StreamRecapRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    ViewerRepository viewerRepository(DSLContext dslContext) {
        return new ViewerRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    StreamerRequestRepository streamerRequestRepository(DSLContext dslContext) {
        return new StreamerRequestRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    AdvertiserAccountRepository advertiserAccountRepository(DSLContext dslContext) {
        return new AdvertiserAccountRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    SessionAuthenticityRepository sessionAuthenticityRepository(DSLContext dslContext) {
        return new SessionAuthenticityRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    ChannelAuthenticityRepository channelAuthenticityRepository(DSLContext dslContext) {
        return new ChannelAuthenticityRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    SocialBladeRepository socialBladeRepository(DSLContext dslContext) {
        return new SocialBladeRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    BrandSafetyRepository brandSafetyRepository(DSLContext dslContext) {
        return new BrandSafetyRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    AlertRepository alertRepository(DSLContext dslContext) {
        return new AlertRepositoryImpl(dslContext);
    }

    @Bean
    @ConditionalOnMissingBean
    CampaignRepository campaignRepository(DSLContext dslContext) {
        return new CampaignRepositoryImpl(dslContext);
    }
}
