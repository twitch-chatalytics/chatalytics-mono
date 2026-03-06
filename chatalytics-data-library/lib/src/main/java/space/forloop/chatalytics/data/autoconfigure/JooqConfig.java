package space.forloop.chatalytics.data.autoconfigure;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@AutoConfigureAfter(JooqAutoConfiguration.class)
public class JooqConfig {

    @Bean
    @ConditionalOnMissingBean
    DSLContext dslContext(DataSource dataSource) {
        return DSL.using(dataSource, SQLDialect.POSTGRES);
    }

}
