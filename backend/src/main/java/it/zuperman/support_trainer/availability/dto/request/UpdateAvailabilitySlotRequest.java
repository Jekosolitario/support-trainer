package it.zuperman.support_trainer.availability.dto.request;

import java.time.LocalDateTime;

public class UpdateAvailabilitySlotRequest {

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    public UpdateAvailabilitySlotRequest() {
    }

    public UpdateAvailabilitySlotRequest(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
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
}