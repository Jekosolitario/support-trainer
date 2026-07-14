package it.zuperman.support_trainer.invite.dto.response;

import java.time.Instant;

import it.zuperman.support_trainer.invite.entity.InviteCode;

public class InviteCodeResponse {

    private Long id;
    private String code;
    private Long professionalId;
    private Instant expiresAt;
    private Boolean used;
    private Instant usedAt;
    private Boolean active;
    private Instant createdAt;

    public InviteCodeResponse() {
    }

    public InviteCodeResponse(
            Long id,
            String code,
            Long professionalId,
            Instant expiresAt,
            Boolean used,
            Instant usedAt,
            Boolean active,
            Instant createdAt
    ) {
        this.id = id;
        this.code = code;
        this.professionalId = professionalId;
        this.expiresAt = expiresAt;
        this.used = used;
        this.usedAt = usedAt;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static InviteCodeResponse fromEntity(InviteCode inviteCode) {
        return new InviteCodeResponse(
                inviteCode.getId(),
                inviteCode.getCode(),
                inviteCode.getProfessional().getId(),
                inviteCode.getExpiresAt(),
                inviteCode.getUsed(),
                inviteCode.getUsedAt(),
                inviteCode.getActive(),
                inviteCode.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
