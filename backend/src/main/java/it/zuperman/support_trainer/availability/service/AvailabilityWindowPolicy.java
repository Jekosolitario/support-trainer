package it.zuperman.support_trainer.availability.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;

public final class AvailabilityWindowPolicy {

    public static final int START_INTERVAL_MINUTES = 15;
    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 180;

    private AvailabilityWindowPolicy() {
    }

    public static boolean isAligned(LocalTime value) {
        return value != null
                && value.getMinute() % START_INTERVAL_MINUTES == 0
                && value.getSecond() == 0
                && value.getNano() == 0;
    }

    public static boolean isAllowedDuration(int durationMinutes) {
        return durationMinutes >= MIN_DURATION_MINUTES
                && durationMinutes <= MAX_DURATION_MINUTES
                && durationMinutes % START_INTERVAL_MINUTES == 0;
    }

    public static Optional<ConcreteWindow> resolveWindow(
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            ZoneId zone
    ) {
        LocalDateTime localStart = LocalDateTime.of(date, startTime);
        LocalDateTime localEnd = LocalDateTime.of(date, endTime);
        Optional<Instant> start = resolveUnambiguous(localStart, zone);
        Optional<Instant> end = resolveUnambiguous(localEnd, zone);
        if (start.isEmpty() || end.isEmpty() || !start.get().isBefore(end.get())) {
            return Optional.empty();
        }

        long civilMinutes = Duration.between(localStart, localEnd).toMinutes();
        long elapsedMinutes = Duration.between(start.get(), end.get()).toMinutes();
        if (civilMinutes != elapsedMinutes) {
            return Optional.empty();
        }
        return Optional.of(new ConcreteWindow(start.get(), end.get()));
    }

    public static Optional<Instant> resolveBookingEnd(
            OffsetDateTime requestedStart,
            int durationMinutes,
            ZoneId zone
    ) {
        LocalDateTime localEnd = requestedStart.toLocalDateTime().plusMinutes(durationMinutes);
        Optional<Instant> resolvedEnd = resolveUnambiguous(localEnd, zone);
        if (resolvedEnd.isEmpty()) {
            return Optional.empty();
        }
        long elapsedMinutes = Duration.between(requestedStart.toInstant(), resolvedEnd.get()).toMinutes();
        return elapsedMinutes == durationMinutes ? resolvedEnd : Optional.empty();
    }

    public static Optional<ConcreteWindow> resolveBookingInterval(
            AvailabilitySlot slot,
            OffsetDateTime requestedStart,
            int durationMinutes,
            ZoneId zone
    ) {
        if (slot == null
                || requestedStart == null
                || !isAligned(requestedStart.toLocalTime())
                || !isAllowedDuration(durationMinutes)
                || !allowedDurations(slot).contains(durationMinutes)) {
            return Optional.empty();
        }

        List<ZoneOffset> startOffsets = zone.getRules().getValidOffsets(requestedStart.toLocalDateTime());
        if (startOffsets.size() != 1 || !startOffsets.getFirst().equals(requestedStart.getOffset())) {
            return Optional.empty();
        }

        Instant start = requestedStart.toInstant();
        Optional<Instant> end = resolveBookingEnd(requestedStart, durationMinutes, zone);
        if (end.isEmpty()
                || start.isBefore(slot.getStartDateTime())
                || end.get().isAfter(slot.getEndDateTime())) {
            return Optional.empty();
        }
        return Optional.of(new ConcreteWindow(start, end.get()));
    }

    public static List<Integer> allowedDurations(AvailabilitySlot slot) {
        if (slot.getWeeklyRule() != null) {
            return slot.getWeeklyRule().getAllowedDurations().stream().sorted().toList();
        }

        long duration = Duration.between(slot.getStartDateTime(), slot.getEndDateTime()).toMinutes();
        if (duration <= Integer.MAX_VALUE && isAllowedDuration((int) duration)) {
            return List.of((int) duration);
        }
        return List.of();
    }

    private static Optional<Instant> resolveUnambiguous(LocalDateTime localDateTime, ZoneId zone) {
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(localDateTime);
        return offsets.size() == 1
                ? Optional.of(localDateTime.toInstant(offsets.getFirst()))
                : Optional.empty();
    }

    public record ConcreteWindow(Instant start, Instant end) {
    }
}
