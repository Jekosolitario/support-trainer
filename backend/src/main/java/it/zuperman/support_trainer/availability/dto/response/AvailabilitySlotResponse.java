package it.zuperman.support_trainer.availability.dto.response;

import java.time.OffsetDateTime;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;

public class AvailabilitySlotResponse {

    private Long id;
    private OffsetDateTime startDateTime;
    private OffsetDateTime endDateTime;
    private String status;
    private Boolean active;

    public AvailabilitySlotResponse() {
    }

    public AvailabilitySlotResponse(
            Long id,
            OffsetDateTime startDateTime,
            OffsetDateTime endDateTime,
            String status,
            Boolean active
    ) {
        this.id = id;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = status;
        this.active = active;
    }

    public static AvailabilitySlotResponse fromEntity(
            AvailabilitySlot slot,
            BusinessDateTimeMapper businessDateTimeMapper
    ) {
        return new AvailabilitySlotResponse(
                slot.getId(),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getStartDateTime()),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getEndDateTime()),
                slot.getStatus() != null ? slot.getStatus().name() : null,
                slot.getActive()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public OffsetDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(OffsetDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public OffsetDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(OffsetDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
