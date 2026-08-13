package it.zuperman.support_trainer.availability.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class AvailabilityWindowPolicyTest {

    private static final ZoneId EUROPE_ROME = ZoneId.of("Europe/Rome");

    @Test
    void shouldResolveNormalCivilWindowWithoutChangingElapsedDuration() {
        var window = AvailabilityWindowPolicy.resolveWindow(
                LocalDate.of(2026, 10, 18),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                EUROPE_ROME
        ).orElseThrow();

        assertThat(Duration.between(window.start(), window.end()).toMinutes()).isEqualTo(120);
    }

    @Test
    void shouldRejectSpringGapWindowAndBookingCombination() {
        assertThat(AvailabilityWindowPolicy.resolveWindow(
                LocalDate.of(2026, 3, 29),
                LocalTime.of(2, 0),
                LocalTime.of(3, 0),
                EUROPE_ROME
        )).isEmpty();
        assertThat(AvailabilityWindowPolicy.resolveBookingEnd(
                OffsetDateTime.parse("2026-03-29T01:30:00+01:00"),
                60,
                EUROPE_ROME
        )).isEmpty();
    }

    @Test
    void shouldRejectAutumnOverlapWithoutSilentlyDoublingElapsedDuration() {
        assertThat(AvailabilityWindowPolicy.resolveWindow(
                LocalDate.of(2026, 10, 25),
                LocalTime.of(2, 0),
                LocalTime.of(3, 0),
                EUROPE_ROME
        )).isEmpty();
        assertThat(AvailabilityWindowPolicy.resolveWindow(
                LocalDate.of(2026, 10, 25),
                LocalTime.of(1, 0),
                LocalTime.of(4, 0),
                EUROPE_ROME
        )).isEmpty();
        assertThat(AvailabilityWindowPolicy.resolveBookingEnd(
                OffsetDateTime.parse("2026-10-25T02:00:00+02:00"),
                60,
                EUROPE_ROME
        )).isEmpty();
    }
}
