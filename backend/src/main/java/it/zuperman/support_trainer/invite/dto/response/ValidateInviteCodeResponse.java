package it.zuperman.support_trainer.invite.dto.response;

import java.time.LocalDateTime;

import it.zuperman.support_trainer.invite.entity.InviteCode;

public class ValidateInviteCodeResponse {

    private Boolean valid;
    private String code;
    private Long professionalId;
    private LocalDateTime expiresAt;

    public ValidateInviteCodeResponse() {
    }

    public ValidateInviteCodeResponse(
            Boolean valid,
            String code,
            Long professionalId,
            LocalDateTime expiresAt
    ) {
        this.valid = valid;
        this.code = code;
        this.professionalId = professionalId;
        this.expiresAt = expiresAt;
    }

    public static ValidateInviteCodeResponse fromEntity(InviteCode inviteCode) {
        return new ValidateInviteCodeResponse(
                true,
                inviteCode.getCode(),
                inviteCode.getProfessional().getId(),
                inviteCode.getExpiresAt()
        );
    }

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getProfessionalId() {
        return professionalId;
    }

    public void setProfessionalId(Long professionalId) {
        this.professionalId = professionalId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}