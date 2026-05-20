package it.zuperman.support_trainer.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateBookingRequest {

    @NotNull(message = "Lo slot disponibilità è obbligatorio")
    private Long availabilitySlotId;

    @Size(max = 1000, message = "La nota non può superare 1000 caratteri")
    private String note;

    public CreateBookingRequest() {
    }

    public CreateBookingRequest(Long availabilitySlotId, String note) {
        this.availabilitySlotId = availabilitySlotId;
        this.note = note;
    }

    public Long getAvailabilitySlotId() {
        return availabilitySlotId;
    }

    public void setAvailabilitySlotId(Long availabilitySlotId) {
        this.availabilitySlotId = availabilitySlotId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
