package it.zuperman.support_trainer.professional.entity;

import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.ProfessionalOperationalStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "professional_profiles")
@PrimaryKeyJoinColumn(name = "id")
public class ProfessionalProfile extends User {

    @Enumerated(EnumType.STRING)
    @Column(name = "specialization", nullable = false, length = 100)
    private ProfessionalSpecialization specialization;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 50)
    private ProfessionalOperationalStatus operationalStatus
            = ProfessionalOperationalStatus.DISPONIBILE;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "workplace_name", length = 150)
    private String workplaceName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public ProfessionalProfile(
            String firstName,
            String lastName,
            String email,
            String password,
            ProfessionalSpecialization specialization
    ) {
        super(Role.PROFESSIONAL, firstName, lastName, email, password);
        this.specialization = specialization;
    }
}
