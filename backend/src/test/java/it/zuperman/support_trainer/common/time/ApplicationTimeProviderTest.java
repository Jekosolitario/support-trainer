package it.zuperman.support_trainer.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("default-time-zone")
class ApplicationTimeProviderTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");
    private static final TimeProperties TIME_PROPERTIES = new TimeProperties(BUSINESS_ZONE, ZoneId.of("UTC"));

    @Test
    void shouldExposeFixedInstantBusinessDateTimeAndBusinessDate() {
        Instant fixedInstant = Instant.parse("2026-07-13T15:30:45Z");
        ApplicationTimeProvider timeProvider = fixedTimeProvider(fixedInstant);

        assertThat(timeProvider.nowInstant()).isEqualTo(fixedInstant);
        assertThat(timeProvider.nowBusinessDateTime()).isEqualTo(LocalDateTime.of(2026, 7, 13, 17, 30, 45));
        assertThat(timeProvider.todayBusiness()).isEqualTo(LocalDate.of(2026, 7, 13));
        assertThat(timeProvider.businessZone()).isEqualTo(BUSINESS_ZONE);
    }

    @Test
    void shouldTruncateNanosecondsToMicrosecondsWithoutRounding() {
        Instant nanosecondInstant = Instant.parse("2026-07-13T15:30:45.123456999Z");
        ApplicationTimeProvider timeProvider = fixedTimeProvider(nanosecondInstant);

        assertThat(timeProvider.nowInstant())
                .isEqualTo(Instant.parse("2026-07-13T15:30:45.123456Z"));
        assertThat(timeProvider.nowInstant().getNano() % 1_000).isZero();
    }

    @Test
    void shouldConvertSummerInstantUsingDaylightSavingOffset() {
        ApplicationTimeProvider timeProvider = fixedTimeProvider(Instant.parse("2026-07-13T15:30:00Z"));

        assertThat(timeProvider.toBusinessDateTime(Instant.parse("2026-07-13T15:30:00Z")))
                .isEqualTo(LocalDateTime.of(2026, 7, 13, 17, 30));
    }

    @Test
    void shouldConvertWinterInstantUsingStandardOffset() {
        ApplicationTimeProvider timeProvider = fixedTimeProvider(Instant.parse("2026-01-13T15:30:00Z"));

        assertThat(timeProvider.toBusinessDateTime(Instant.parse("2026-01-13T15:30:00Z")))
                .isEqualTo(LocalDateTime.of(2026, 1, 13, 16, 30));
    }

    @Test
    void shouldNotDependOnJvmDefaultTimeZone() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        ApplicationTimeProvider timeProvider = fixedTimeProvider(Instant.parse("2026-07-13T15:30:45Z"));

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            LocalDateTime honoluluJvmResult = timeProvider.nowBusinessDateTime();

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            LocalDateTime tokyoJvmResult = timeProvider.nowBusinessDateTime();

            assertThat(honoluluJvmResult).isEqualTo(LocalDateTime.of(2026, 7, 13, 17, 30, 45));
            assertThat(tokyoJvmResult).isEqualTo(honoluluJvmResult);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static ApplicationTimeProvider fixedTimeProvider(Instant instant) {
        return new ApplicationTimeProvider(Clock.fixed(instant, ZoneOffset.UTC), TIME_PROPERTIES);
    }
}
