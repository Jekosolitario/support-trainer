package it.zuperman.support_trainer.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(ApplicationAuditingIntegrationTest.FixedClockConfiguration.class)
@Transactional
class ApplicationAuditingIntegrationTest {

    private static final Instant INITIAL_INSTANT = Instant.parse("2026-07-13T15:30:45.123456999Z");
    private static final Instant INITIAL_MICRO_INSTANT = Instant.parse("2026-07-13T15:30:45.123456Z");
    private static final Instant UPDATED_INSTANT = Instant.parse("2026-07-13T16:00:00.654321999Z");
    private static final Instant UPDATED_MICRO_INSTANT = Instant.parse("2026-07-13T16:00:00.654321Z");

    @Autowired
    private ProfessionalProfileRepository professionalRepository;

    @Autowired
    private MutableFixedClock clock;

    @BeforeEach
    void resetClock() {
        clock.setInstant(INITIAL_INSTANT);
    }

    @Test
    void shouldCreateAndUpdateAuditInstantsFromTheApplicationClock() {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Audit",
                "Tester",
                "audit-" + UUID.randomUUID() + "@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        ProfessionalProfile saved = professionalRepository.saveAndFlush(professional);

        assertThat(saved.getCreatedAt()).isEqualTo(INITIAL_MICRO_INSTANT);
        assertThat(saved.getUpdatedAt()).isEqualTo(INITIAL_MICRO_INSTANT);

        clock.setInstant(UPDATED_INSTANT);
        saved.setCity("Roma");
        ProfessionalProfile updated = professionalRepository.saveAndFlush(saved);

        assertThat(updated.getCreatedAt()).isEqualTo(INITIAL_MICRO_INSTANT);
        assertThat(updated.getUpdatedAt()).isEqualTo(UPDATED_MICRO_INSTANT);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableFixedClock testApplicationClock() {
            return new MutableFixedClock(Clock.fixed(INITIAL_INSTANT, ZoneOffset.UTC));
        }
    }

    static final class MutableFixedClock extends Clock {

        private volatile Clock delegate;

        private MutableFixedClock(Clock delegate) {
            this.delegate = delegate;
        }

        void setInstant(Instant instant) {
            delegate = Clock.fixed(instant, ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(delegate.instant(), zone);
        }

        @Override
        public Instant instant() {
            return delegate.instant();
        }
    }
}
