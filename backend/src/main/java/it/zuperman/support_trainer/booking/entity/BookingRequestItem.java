package it.zuperman.support_trainer.booking.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "booking_request_items")
public class BookingRequestItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_request_id", nullable = false)
    private BookingRequest bookingRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "availability_slot_id", nullable = false)
    private AvailabilitySlot availabilitySlot;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "scheduled_start", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant scheduledStart;

    @Setter(AccessLevel.NONE)
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "scheduled_end", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant scheduledEnd;

    public BookingRequestItem(
            BookingRequest bookingRequest,
            AvailabilitySlot availabilitySlot,
            Instant scheduledStart,
            Instant scheduledEnd
    ) {
        this.bookingRequest = bookingRequest;
        this.availabilitySlot = availabilitySlot;
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
    }
}
