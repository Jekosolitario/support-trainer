package it.zuperman.support_trainer.session;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * Test-only H2 initializer for the official Spring Session JDBC schema.
 * Keeps {@code spring.session.jdbc.initialize-schema=never} while avoiding
 * global {@code spring.sql.init} re-execution on a shared in-memory database.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.h2.Driver")
public class SpringSessionH2SchemaAutoConfiguration {

    private static final String SCHEMA_LOCATION = "session/schema-h2-spring-session-4.0.2.sql";

    @Bean
    ApplicationRunner springSessionH2SchemaInitializer(DataSource dataSource) {
        return args -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE UPPER(TABLE_NAME) = 'SPRING_SESSION'",
                    Integer.class
            );
            if (existing != null && existing > 0) {
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource(SCHEMA_LOCATION));
            populator.setContinueOnError(false);
            populator.execute(dataSource);
        };
    }
}
