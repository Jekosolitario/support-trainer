package it.zuperman.support_trainer.availability.dto.response;

import java.time.LocalDateTime;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;

public class AvailabilitySlotResponse {

    private Long id;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String status;
    private Boolean active;

    public AvailabilitySlotResponse() {
    }

    public AvailabilitySlotResponse(
            Long id,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime,
            String status,
            Boolean active
    ) {
        this.id = id;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = status;
        this.active = active;
    }

    public static AvailabilitySlotResponse fromEntity(AvailabilitySlot slot) {
        return new AvailabilitySlotResponse(
                slot.getId(),
                slot.getStartDateTime(),
                slot.getEndDateTime(),
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

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
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