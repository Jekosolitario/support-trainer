package it.zuperman.support_trainer.email.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class EmailTestClockConfiguration {

    public static final Instant INITIAL_INSTANT = Instant.parse("2026-07-15T10:00:00Z");

    @Bean
    @Primary
    MutableTestClock emailTestClock() {
        return new MutableTestClock(INITIAL_INSTANT);
    }

    public static final class MutableTestClock extends Clock {

        private volatile Clock delegate;

        public MutableTestClock(Instant instant) {
            setInstant(instant);
        }

        public void setInstant(Instant instant) {
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
