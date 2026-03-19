package it.zuperman.support_trainer.client.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.ClientOperationalStatus;
import it.zuperman.support_trainer.common.enums.Gender;
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
@Table(name = "client_profiles")
@PrimaryKeyJoinColumn(name = "id")
public class ClientProfile extends User {

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 50)
    private ClientOperationalStatus operationalStatus
            = ClientOperationalStatus.ATTIVO;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "height_cm", nullable = false, precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "primary_goal", nullable = false, length = 255)
    private String primaryGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 30)
    private Gender gender;

    @Column(name = "medical_notes", columnDefinition = "TEXT")
    private String medicalNotes;

    @Column(name = "injury_notes", columnDefinition = "TEXT")
    private String injuryNotes;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public ClientProfile(
            String firstName,
            String lastName,
            String email,
            String password,
            LocalDate birthDate,
            BigDecimal heightCm,
            String primaryGoal,
            Gender gender
    ) {
        super(Role.CLIENT, firstName, lastName, email, password);
        this.birthDate = birthDate;
        this.heightCm = heightCm;
        this.primaryGoal = primaryGoal;
        this.gender = gender;
    }
}
