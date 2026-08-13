package it.zuperman.support_trainer.booking.dto.response;

import java.time.OffsetDateTime;

public class BookingItemResponse {

    private final Long id;
    private final Long availabilitySlotId;
    private final OffsetDateTime scheduledStart;
    private final OffsetDateTime scheduledEnd;
    private final long durationMinutes;
    private final String locationLabel;

    public BookingItemResponse(
            Long id,
            Long availabilitySlotId,
            OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd,
            long durationMinutes
    ) {
        this(id, availabilitySlotId, scheduledStart, scheduledEnd, durationMinutes, null);
    }

    public BookingItemResponse(
            Long id,
            Long availabilitySlotId,
            OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd,
            long durationMinutes,
            String locationLabel
    ) {
        this.id = id;
        this.availabilitySlotId = availabilitySlotId;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.durationMinutes = durationMinutes;
        this.locationLabel = locationLabel;
    }

    public Long getId() {
        return id;
    }

    public Long getAvailabilitySlotId() {
        return availabilitySlotId;
    }

    public OffsetDateTime getScheduledStart() {
        return scheduledStart;
    }

    public OffsetDateTime getScheduledEnd() {
        return scheduledEnd;
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public String getLocationLabel() {
        return locationLabel;
    }
}
