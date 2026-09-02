package it.zuperman.support_trainer.auth.dto.request;

import it.zuperman.support_trainer.security.password.BcryptCompatiblePassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordRecoveryConfirmRequest {

    @NotBlank(message = "Il token di recupero è obbligatorio")
    @Size(max = 500, message = "Il token di recupero non può superare 500 caratteri")
    private String token;

    @NotBlank(message = "La password è obbligatoria")
    @Size(min = 8, message = "La password deve contenere almeno 8 caratteri")
    @BcryptCompatiblePassword
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "La password deve contenere almeno una maiuscola, un numero e un carattere speciale"
    )
    private String newPassword;

    public PasswordRecoveryConfirmRequest() {
    }

    public PasswordRecoveryConfirmRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    @Override
    public String toString() {
        return "PasswordRecoveryConfirmRequest[]";
    }
}
