package it.zuperman.support_trainer.availability.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_rule_id")
    private WeeklyAvailabilityRule weeklyRule;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "start_date_time", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant startDateTime;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "end_date_time", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant endDateTime;

    @Column(name = "location_label", length = 255)
    private String locationLabel;

    @Column(name = "capacity", nullable = false)
    private Integer capacity = 1;

    @Column(name = "blocked", nullable = false)
    private Boolean blocked = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AvailabilitySlotStatus status = AvailabilitySlotStatus.AVAILABLE;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    public AvailabilitySlot(
            ProfessionalProfile professional,
            Instant startDateTime,
            Instant endDateTime
    ) {
        this.professional = professional;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.status = AvailabilitySlotStatus.AVAILABLE;
        this.capacity = 1;
        this.blocked = false;
        this.active = true;
    }

    public AvailabilitySlot(
            ProfessionalProfile professional,
            WeeklyAvailabilityRule weeklyRule,
            Instant startDateTime,
            Instant endDateTime,
            String locationLabel,
            Integer capacity
    ) {
        this.professional = professional;
        this.weeklyRule = weeklyRule;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.locationLabel = locationLabel;
        this.capacity = capacity;
        this.blocked = false;
        this.status = AvailabilitySlotStatus.AVAILABLE;
        this.active = true;
    }
}
