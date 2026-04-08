package it.zuperman.support_trainer.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateOperationalStatusRequest {

    @NotBlank(message = "Lo stato operativo è obbligatorio")
    @Size(max = 50, message = "Lo stato operativo non può superare 50 caratteri")
    private String operationalStatus;

    public UpdateOperationalStatusRequest() {
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }
}