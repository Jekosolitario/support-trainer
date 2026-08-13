package it.zuperman.support_trainer.availability.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;

@Service
public class AvailabilityCapacityService {

    private final BookingRequestItemRepository bookingRequestItemRepository;
    private final ApplicationTimeProvider timeProvider;

    public AvailabilityCapacityService(
            BookingRequestItemRepository bookingRequestItemRepository,
            ApplicationTimeProvider timeProvider
    ) {
        this.bookingRequestItemRepository = bookingRequestItemRepository;
        this.timeProvider = timeProvider;
    }

    public long maximumOccupancy(AvailabilitySlot slot) {
        List<BookingRequestItem> bookings = overlappingBookings(
                slot.getProfessional().getId(),
                slot.getStartDateTime(),
                slot.getEndDateTime()
        );
        return maximumOccupancy(bookings, slot.getStartDateTime(), slot.getEndDateTime());
    }

    public OccupancySnapshot loadOccupancy(List<AvailabilitySlot> slots) {
        if (slots.isEmpty()) {
            return OccupancySnapshot.empty();
        }
        Long professionalId = slots.getFirst().getProfessional().getId();
        if (slots.stream().map(slot -> slot.getProfessional().getId()).anyMatch(id -> !Objects.equals(
                id,
                professionalId
        ))) {
            throw new IllegalArgumentException("Availability batch must belong to one professional");
        }
        Instant rangeStart = slots.stream()
                .map(AvailabilitySlot::getStartDateTime)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        Instant rangeEnd = slots.stream()
                .map(AvailabilitySlot::getEndDateTime)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        return new OccupancySnapshot(bookingRequestItemRepository.findOccupyingBookingsForAvailabilityRange(
                professionalId,
                rangeStart,
                rangeEnd,
                AvailabilityMaterializationService.OCCUPYING_STATUSES
        ));
    }

    public long maximumOccupancy(AvailabilitySlot slot, OccupancySnapshot snapshot) {
        return maximumOccupancy(
                snapshot.overlapping(slot.getStartDateTime(), slot.getEndDateTime()),
                slot.getStartDateTime(),
                slot.getEndDateTime()
        );
    }

    public long occupyingBookingCount(AvailabilitySlot slot) {
        return overlappingBookings(
                slot.getProfessional().getId(),
                slot.getStartDateTime(),
                slot.getEndDateTime()
        ).size();
    }

    public boolean hasCapacity(
            AvailabilitySlot slot,
            Instant requestedStart,
            Instant requestedEnd
    ) {
        List<BookingRequestItem> bookings = overlappingBookings(
                slot.getProfessional().getId(),
                requestedStart,
                requestedEnd
        );
        return maximumOccupancy(bookings, requestedStart, requestedEnd) < slot.getCapacity();
    }

    public boolean hasAnyBookableCombination(AvailabilitySlot slot) {
        return hasAnyBookableCombination(slot, loadOccupancy(List.of(slot)));
    }

    public List<BookableOption> bookableOptions(
            AvailabilitySlot slot,
            OccupancySnapshot snapshot
    ) {
        return calculateBookableOptions(slot, snapshot, List.of());
    }

    public List<BookableOption> bookableOptionsForClient(
            AvailabilitySlot slot,
            OccupancySnapshot snapshot,
            Long clientId
    ) {
        Objects.requireNonNull(clientId, "Client id is required");
        return calculateBookableOptions(
                slot,
                snapshot,
                snapshot.overlappingForClient(
                        clientId,
                        slot.getStartDateTime(),
                        slot.getEndDateTime()
                )
        );
    }

    private List<BookableOption> calculateBookableOptions(
            AvailabilitySlot slot,
            OccupancySnapshot snapshot,
            List<BookingRequestItem> clientBookings
    ) {
        if (!Boolean.TRUE.equals(slot.getActive())
                || Boolean.TRUE.equals(slot.getBlocked())
                || slot.getWeeklyRule() == null
                || !slot.getStartDateTime().isAfter(timeProvider.nowInstant())) {
            return List.of();
        }

        List<Integer> durations = AvailabilityWindowPolicy.allowedDurations(slot);
        if (durations.isEmpty()) {
            return List.of();
        }
        List<BookingRequestItem> bookings = snapshot.overlapping(
                slot.getStartDateTime(),
                slot.getEndDateTime()
        );
        LocalDateTime localStart = LocalDateTime.ofInstant(
                slot.getStartDateTime(),
                timeProvider.businessZone()
        );
        LocalDateTime localEnd = LocalDateTime.ofInstant(
                slot.getEndDateTime(),
                timeProvider.businessZone()
        );
        List<BookableOption> options = new ArrayList<>();

        for (LocalDateTime candidateStart = localStart;
                candidateStart.isBefore(localEnd);
                candidateStart = candidateStart.plusMinutes(AvailabilityWindowPolicy.START_INTERVAL_MINUTES)) {
            List<ZoneOffset> validOffsets = timeProvider.businessZone()
                    .getRules()
                    .getValidOffsets(candidateStart);
            if (validOffsets.size() != 1) {
                continue;
            }
            OffsetDateTime offsetStart = OffsetDateTime.of(candidateStart, validOffsets.getFirst());
            List<Integer> bookableDurations = durations.stream()
                    .filter(duration -> AvailabilityWindowPolicy.resolveBookingInterval(
                            slot,
                            offsetStart,
                            duration,
                            timeProvider.businessZone()
                    ).filter(interval -> !hasOverlap(
                            clientBookings,
                            interval.start(),
                            interval.end()
                    ) && maximumOccupancy(
                            bookings,
                            interval.start(),
                            interval.end()
                    ) < slot.getCapacity()).isPresent())
                    .toList();
            if (!bookableDurations.isEmpty()) {
                options.add(new BookableOption(offsetStart, bookableDurations));
            }
        }
        return List.copyOf(options);
    }

    private static boolean hasOverlap(
            List<BookingRequestItem> bookings,
            Instant requestedStart,
            Instant requestedEnd
    ) {
        return bookings.stream()
                .anyMatch(item -> item.getScheduledStart().isBefore(requestedEnd)
                        && item.getScheduledEnd().isAfter(requestedStart));
    }

    public boolean hasAnyBookableCombination(
            AvailabilitySlot slot,
            OccupancySnapshot snapshot
    ) {
        return !bookableOptions(slot, snapshot).isEmpty();
    }

    private List<BookingRequestItem> overlappingBookings(
            Long professionalId,
            Instant start,
            Instant end
    ) {
        return bookingRequestItemRepository.findOccupyingBookingsOverlappingProfessional(
                professionalId,
                start,
                end,
                AvailabilityMaterializationService.OCCUPYING_STATUSES
        );
    }

    static long maximumOccupancy(
            List<BookingRequestItem> bookings,
            Instant requestedStart,
            Instant requestedEnd
    ) {
        List<Instant> checkpoints = new ArrayList<>();
        checkpoints.add(requestedStart);
        bookings.stream()
                .map(BookingRequestItem::getScheduledStart)
                .filter(start -> !start.isBefore(requestedStart) && start.isBefore(requestedEnd))
                .sorted(Comparator.naturalOrder())
                .forEach(checkpoints::add);

        long maximum = 0;
        for (Instant checkpoint : checkpoints) {
            long occupancy = bookings.stream()
                    .filter(item -> !item.getScheduledStart().isAfter(checkpoint))
                    .filter(item -> item.getScheduledEnd().isAfter(checkpoint))
                    .count();
            maximum = Math.max(maximum, occupancy);
        }
        return maximum;
    }

    public record BookableOption(OffsetDateTime startDateTime, List<Integer> allowedDurations) {
    }

    public record OccupancySnapshot(List<BookingRequestItem> bookings) {

        public OccupancySnapshot {
            bookings = List.copyOf(bookings);
        }

        static OccupancySnapshot empty() {
            return new OccupancySnapshot(List.of());
        }

        List<BookingRequestItem> overlapping(Instant start, Instant end) {
            return bookings.stream()
                    .filter(item -> item.getScheduledStart().isBefore(end))
                    .filter(item -> item.getScheduledEnd().isAfter(start))
                    .toList();
        }

        List<BookingRequestItem> overlappingForClient(Long clientId, Instant start, Instant end) {
            return overlapping(start, end).stream()
                    .filter(item -> Objects.equals(
                            item.getBookingRequest().getClient().getId(),
                            clientId
                    ))
                    .toList();
        }
    }
}
