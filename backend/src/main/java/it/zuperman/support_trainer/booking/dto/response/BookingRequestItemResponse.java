package it.zuperman.support_trainer.booking.dto.response;

import java.time.OffsetDateTime;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.booking.entity.BookingRequestItem;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;

public class BookingRequestItemResponse {

    private Long id;
    private Long availabilitySlotId;
    private OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;
    private String slotStatus;

    public BookingRequestItemResponse() {
    }

    public BookingRequestItemResponse(
            Long id,
            Long availabilitySlotId,
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            String slotStatus
    ) {
        this.id = id;
        this.availabilitySlotId = availabilitySlotId;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.slotStatus = slotStatus;
    }

    public static BookingRequestItemResponse fromEntity(
            BookingRequestItem item,
            BusinessDateTimeMapper businessDateTimeMapper
    ) {
        AvailabilitySlot slot = item.getAvailabilitySlot();

        return new BookingRequestItemResponse(
                item.getId(),
                slot.getId(),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getStartDateTime()),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getEndDateTime()),
                slot.getStatus() != null ? slot.getStatus().name() : null
        );
    }

    public Long getId() {
        return id;
    }

    public Long getAvailabilitySlotId() {
        return availabilitySlotId;
    }

    public OffsetDateTime getStartDateTime() {
        return startDateTime;
    }

    public OffsetDateTime getEndDateTime() {
        return endDateTime;
    }

    public String getSlotStatus() {
        return slotStatus;
    }
}
