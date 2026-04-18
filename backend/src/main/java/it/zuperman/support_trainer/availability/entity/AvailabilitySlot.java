package it.zuperman.support_trainer.availability.entity;

import java.time.LocalDateTime;

import it.zuperman.support_trainer.common.entity.BaseEntity;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "availability_slots")
public class AvailabilitySlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private ProfessionalProfile professional;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AvailabilitySlotStatus status = AvailabilitySlotStatus.AVAILABLE;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public AvailabilitySlot(
            ProfessionalProfile professional,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    ) {
        this.professional = professional;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = AvailabilitySlotStatus.AVAILABLE;
        this.active = true;
    }
}