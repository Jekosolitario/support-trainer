package it.zuperman.support_trainer.booking.dto.response;

import java.time.Instant;
import java.time.OffsetDateTime;

public class BookingSummaryResponse {

    private final Long id;
    private final String status;
    private final BookingParticipantResponse counterparty;
    private final OffsetDateTime scheduledStart;
    private final OffsetDateTime scheduledEnd;
    private final long durationMinutes;
    private final String note;
    private final Instant createdAt;

    public BookingSummaryResponse(
            Long id,
            String status,
            BookingParticipantResponse counterparty,
            OffsetDateTime scheduledStart,
            OffsetDateTime scheduledEnd,
            long durationMinutes,
            String note,
            Instant createdAt
    ) {
        this.id = id;
        this.status = status;
        this.counterparty = counterparty;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.durationMinutes = durationMinutes;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public BookingParticipantResponse getCounterparty() {
        return counterparty;
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
}
