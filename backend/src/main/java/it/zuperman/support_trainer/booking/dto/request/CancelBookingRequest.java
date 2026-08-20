package it.zuperman.support_trainer.booking.dto.request;

import jakarta.validation.constraints.Size;

public class CancelBookingRequest {

    @Size(max = 1000, message = "La motivazione non può superare 1000 caratteri")
    private String reason;

    public CancelBookingRequest() {
    }

    public CancelBookingRequest(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
