package it.zuperman.support_trainer.booking.entity;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.common.entity.BaseEntity;
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

    public BookingRequestItem(
            BookingRequest bookingRequest,
            AvailabilitySlot availabilitySlot
    ) {
        this.bookingRequest = bookingRequest;
        this.availabilitySlot = availabilitySlot;
    }
}