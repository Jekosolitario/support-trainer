package it.zuperman.support_trainer.availability.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

import it.zuperman.support_trainer.common.entity.BaseEntity;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "weekly_availability_rules")
public class WeeklyAvailabilityRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private ProfessionalProfile professional;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 16)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false, columnDefinition = "TIME(0)")
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false, columnDefinition = "TIME(0)")
    private LocalTime endTime;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "weekly_availability_rule_durations",
            joinColumns = @JoinColumn(name = "weekly_rule_id")
    )
    @Column(name = "duration_minutes", nullable = false)
    private Set<Integer> allowedDurations = new LinkedHashSet<>();

    @Column(name = "location_label", length = 255)
    private String locationLabel;

    @Column(name = "capacity_per_slot", nullable = false)
    private Integer capacityPerSlot;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    public WeeklyAvailabilityRule(
            ProfessionalProfile professional,
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Set<Integer> allowedDurations,
            String locationLabel,
            Integer capacityPerSlot,
            LocalDate validFrom
    ) {
        this.professional = professional;
        replaceSchedule(
                dayOfWeek,
                startTime,
                endTime,
                allowedDurations,
                locationLabel,
                capacityPerSlot,
                validFrom
        );
        this.active = true;
    }

    public void replaceSchedule(
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Set<Integer> allowedDurations,
            String locationLabel,
            Integer capacityPerSlot
    ) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.allowedDurations.clear();
        this.allowedDurations.addAll(allowedDurations);
        this.locationLabel = locationLabel;
        this.capacityPerSlot = capacityPerSlot;
    }

    public void replaceSchedule(
            DayOfWeek dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Set<Integer> allowedDurations,
            String locationLabel,
            Integer capacityPerSlot,
            LocalDate validFrom
    ) {
        replaceSchedule(
                dayOfWeek,
                startTime,
                endTime,
                allowedDurations,
                locationLabel,
                capacityPerSlot
        );
        this.validFrom = validFrom;
    }

    public void deactivate() {
        this.active = false;
    }
}
