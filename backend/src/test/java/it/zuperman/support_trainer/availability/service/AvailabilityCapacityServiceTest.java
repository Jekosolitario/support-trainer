package it.zuperman.support_trainer.availability.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

class AvailabilityCapacityServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Rome");

    @Test
    void shouldLoadOccupancyOnceAndCalculateEveryOccurrenceInMemory() {
        BookingRequestItemRepository repository = mock(BookingRequestItemRepository.class);
        ApplicationTimeProvider timeProvider = mock(ApplicationTimeProvider.class);
        AvailabilityCapacityService service = new AvailabilityCapacityService(repository, timeProvider);
        ProfessionalProfile professional = mock(ProfessionalProfile.class);
        when(professional.getId()).thenReturn(7L);
        when(timeProvider.businessZone()).thenReturn(BUSINESS_ZONE);
        when(timeProvider.nowInstant()).thenReturn(Instant.parse("2026-08-12T07:00:00Z"));
        when(repository.findOccupyingBookingsForAvailabilityRange(
                eq(7L),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        WeeklyAvailabilityRule rule = new WeeklyAvailabilityRule(
                professional,
                java.time.DayOfWeek.THURSDAY,
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                Set.of(45, 60),
                "Studio",
                2,
                LocalDate.of(2026, 8, 13)
        );
        AvailabilitySlot first = slot(professional, rule, LocalDate.of(2026, 8, 13));
        AvailabilitySlot second = slot(professional, rule, LocalDate.of(2026, 8, 20));

        AvailabilityCapacityService.OccupancySnapshot snapshot
                = service.loadOccupancy(List.of(first, second));

        assertThat(service.bookableOptions(first, snapshot)).isNotEmpty();
        assertThat(service.bookableOptions(second, snapshot)).isNotEmpty();
        assertThat(service.bookableOptionsForClient(first, snapshot, 11L)).isNotEmpty();
        assertThat(service.maximumOccupancy(first, snapshot)).isZero();
        assertThat(service.maximumOccupancy(second, snapshot)).isZero();
        verify(repository, times(1)).findOccupyingBookingsForAvailabilityRange(
                eq(7L),
                any(),
                any(),
                any()
        );
    }

    private AvailabilitySlot slot(
            ProfessionalProfile professional,
            WeeklyAvailabilityRule rule,
            LocalDate date
    ) {
        Instant start = date.atTime(9, 0).atZone(BUSINESS_ZONE).toInstant();
        return new AvailabilitySlot(
                professional,
                rule,
                start,
                date.atTime(11, 0).atZone(BUSINESS_ZONE).toInstant(),
                "Studio",
                2
        );
    }
}
