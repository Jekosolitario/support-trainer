package it.zuperman.support_trainer.booking.dto.response;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public class BookingDetailResponse {

    private final Long id;
    private final String status;
    private final BookingParticipantResponse client;
    private final BookingParticipantResponse professional;
    private final OffsetDateTime scheduledStart;
    private final OffsetDateTime scheduledEnd;
    private final long durationMinutes;
    private final String note;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant confirmedAt;
    private final Instant rejectedAt;
    private final Instant cancelledAt;
    private final List<BookingItemResponse> items;

    public BookingDetailResponse(
            Long id,
            String status,
            BookingParticipantResponse client,
            BookingParticipantResponse professional,
            OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd,
            long durationMinutes,
            String note,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt,
            Instant rejectedAt,
            Instant cancelledAt,
            List<BookingItemResponse> items
    ) {
        this.id = id;
        this.status = status;
        this.client = client;
        this.professional = professional;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.durationMinutes = durationMinutes;
        this.note = note;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.confirmedAt = confirmedAt;
        this.rejectedAt = rejectedAt;
        this.cancelledAt = cancelledAt;
        this.items = items;
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public BookingParticipantResponse getClient() {
        return client;
    }

    public BookingParticipantResponse getProfessional() {
        return professional;
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

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public List<BookingItemResponse> getItems() {
        return items;
    }
}
