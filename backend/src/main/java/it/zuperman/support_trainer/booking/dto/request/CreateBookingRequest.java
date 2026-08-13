package it.zuperman.support_trainer.booking.dto.request;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateBookingRequest {

    @NotNull(message = "Lo slot disponibilità è obbligatorio")
    private Long availabilitySlotId;

    @NotNull(message = "L'orario di inizio è obbligatorio")
    @JsonFormat(without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    private OffsetDateTime startDateTime;

    @NotNull(message = "La durata è obbligatoria")
    @Min(value = 15, message = "La durata minima è 15 minuti")
    @Max(value = 180, message = "La durata massima è 180 minuti")
    private Integer durationMinutes;

    @Size(max = 1000, message = "La nota non può superare 1000 caratteri")
    private String note;

    public CreateBookingRequest() {
    }

    public CreateBookingRequest(
            Long availabilitySlotId,
            OffsetDateTime startDateTime,
            Integer durationMinutes,
            String note
    ) {
        this.availabilitySlotId = availabilitySlotId;
        this.startDateTime = startDateTime;
        this.durationMinutes = durationMinutes;
        this.note = note;
    }

    public Long getAvailabilitySlotId() {
        return availabilitySlotId;
    }

    public void setAvailabilitySlotId(Long availabilitySlotId) {
        this.availabilitySlotId = availabilitySlotId;
    }

    public OffsetDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(OffsetDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
