package it.zuperman.support_trainer.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResendEmailVerificationRequest {

    private String email;

    public ResendEmailVerificationRequest() {
    }

    public ResendEmailVerificationRequest(String email) {
        this.email = email;
    }

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Formato email non valido")
    @Size(max = 100, message = "L'email non può superare 100 caratteri")
    public String getEmail() {
        return email == null ? null : email.trim();
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
