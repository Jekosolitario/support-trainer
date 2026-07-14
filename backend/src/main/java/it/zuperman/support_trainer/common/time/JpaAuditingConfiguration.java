package it.zuperman.support_trainer.common.time;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "applicationDateTimeProvider")
public class JpaAuditingConfiguration {

    @Bean
    public DateTimeProvider applicationDateTimeProvider(ApplicationTimeProvider timeProvider) {
        return () -> Optional.of(timeProvider.nowInstant());
    }
}
