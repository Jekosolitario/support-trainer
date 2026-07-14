package it.zuperman.support_trainer.invite.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "expires_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private Boolean used = false;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "used_at", columnDefinition = "DATETIME(6)")
    private Instant usedAt;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public InviteCode(
            String code,
            ProfessionalProfile professional,
            Instant expiresAt
    ) {
        this.code = code;
        this.professional = professional;
        this.expiresAt = expiresAt;
    }
}
