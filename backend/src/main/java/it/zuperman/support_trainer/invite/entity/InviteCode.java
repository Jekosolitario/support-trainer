package it.zuperman.support_trainer.invite.entity;

import java.time.LocalDateTime;

import it.zuperman.support_trainer.common.entity.BaseEntity;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "invite_codes")
public class InviteCode extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private ProfessionalProfile professional;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    private Boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public InviteCode(
            String code,
            ProfessionalProfile professional,
            LocalDateTime expiresAt
    ) {
        this.code = code;
        this.professional = professional;
        this.expiresAt = expiresAt;
    }
}
