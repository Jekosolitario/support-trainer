package it.zuperman.support_trainer.availability.dto.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public class CreateAvailabilitySlotRequest {

    @NotNull(message = "La data/ora di inizio è obbligatoria")
    private LocalDateTime startDateTime;

    @NotNull(message = "La data/ora di fine è obbligatoria")
    private LocalDateTime endDateTime;

    public CreateAvailabilitySlotRequest() {
    }

    public CreateAvailabilitySlotRequest(LocalDateTime startDateTime, LocalDateTime endDateTime) {
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