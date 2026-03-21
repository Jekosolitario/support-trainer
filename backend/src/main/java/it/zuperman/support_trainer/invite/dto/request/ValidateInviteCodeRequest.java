package it.zuperman.support_trainer.invite.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ValidateInviteCodeRequest {

    @NotBlank(message = "Il codice invito è obbligatorio")
    @Size(max = 100, message = "Il codice invito non può superare 100 caratteri")
    private String code;

    public ValidateInviteCodeRequest() {
    }

    public ValidateInviteCodeRequest(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}