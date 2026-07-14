package it.zuperman.support_trainer.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ConfirmEmailVerificationRequest {

    @NotBlank(message = "Il token di verifica è obbligatorio")
    @Size(max = 500, message = "Il token di verifica non può superare 500 caratteri")
    private String token;

    public ConfirmEmailVerificationRequest() {
    }

    public ConfirmEmailVerificationRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
