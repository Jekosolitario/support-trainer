package it.zuperman.support_trainer.availability.entity;

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
@Table(name = "availability_slot_changes")
public class AvailabilitySlotChange extends BaseEntity {

    public enum ChangeType {
        BLOCK,
        UNBLOCK
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "availability_slot_id", nullable = false)
    private AvailabilitySlot availabilitySlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 32)
    private ChangeType changeType;

    @Column(name = "change_reason", length = 1000)
    private String changeReason;

    @Column(name = "impacted_booking_count", nullable = false)
    private Long impactedBookingCount;

    public AvailabilitySlotChange(
            AvailabilitySlot availabilitySlot,
            ChangeType changeType,
            String changeReason,
            long impactedBookingCount
    ) {
        this.availabilitySlot = availabilitySlot;
        this.changeType = changeType;
        this.changeReason = changeReason;
        this.impactedBookingCount = impactedBookingCount;
    }
}
