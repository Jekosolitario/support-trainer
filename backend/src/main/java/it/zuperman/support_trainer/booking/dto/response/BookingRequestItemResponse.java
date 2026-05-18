package it.zuperman.support_trainer.booking.dto.response;

import java.time.LocalDateTime;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;

public class BookingRequestItemResponse {

    private Long id;
    private Long availabilitySlotId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String slotStatus;

    public BookingRequestItemResponse() {
    }

    public BookingRequestItemResponse(
            Long id,
            Long availabilitySlotId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String slotStatus
    ) {
        this.id = id;
        this.availabilitySlotId = availabilitySlotId;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.slotStatus = slotStatus;
    }

    public static BookingRequestItemResponse fromEntity(BookingRequestItem item) {
        AvailabilitySlot slot = item.getAvailabilitySlot();

        return new BookingRequestItemResponse(
                item.getId(),
                slot.getId(),
                slot.getStartDateTime(),
                slot.getEndDateTime(),
                slot.getStatus() != null ? slot.getStatus().name() : null
        );
    }

    public Long getId() {
        return id;
    }

    public Long getAvailabilitySlotId() {
        return availabilitySlotId;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public String getSlotStatus() {
        return slotStatus;
    }
}