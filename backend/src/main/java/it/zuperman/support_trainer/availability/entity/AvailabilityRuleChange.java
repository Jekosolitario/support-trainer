package it.zuperman.support_trainer.availability.entity;

import java.time.LocalDate;

import it.zuperman.support_trainer.common.entity.BaseEntity;
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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "availability_rule_changes")
public class AvailabilityRuleChange extends BaseEntity {

    public enum ChangeType {
        UPDATE,
        DEACTIVATE
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "weekly_rule_id", nullable = false)
    private WeeklyAvailabilityRule weeklyRule;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 32)
    private ChangeType changeType;

    @Column(name = "change_reason", length = 1000)
    private String changeReason;

    @Column(name = "impacted_booking_count", nullable = false)
    private Long impactedBookingCount;

    public AvailabilityRuleChange(
            WeeklyAvailabilityRule weeklyRule,
            LocalDate effectiveFrom,
            ChangeType changeType,
            String changeReason,
            long impactedBookingCount
    ) {
        this.weeklyRule = weeklyRule;
        this.effectiveFrom = effectiveFrom;
        this.changeType = changeType;
        this.changeReason = changeReason;
        this.impactedBookingCount = impactedBookingCount;
    }
}
