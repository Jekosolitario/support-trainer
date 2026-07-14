package it.zuperman.support_trainer.availability.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import jakarta.persistence.LockModeType;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    boolean existsByProfessional_IdAndActiveTrueAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            Long professionalId,
            Instant endDateTime,
            Instant startDateTime
    );

    boolean existsByProfessional_IdAndActiveTrueAndIdNotAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            Long professionalId,
            Long slotId,
            Instant endDateTime,
            Instant startDateTime
    );

    List<AvailabilitySlot> findAllByProfessional_IdAndActiveTrueOrderByStartDateTimeAsc(Long professionalId);

    @Query("SELECT slot FROM AvailabilitySlot slot "
        + "WHERE slot.professional.id = :professionalId "
        + "AND slot.active = true "
        + "AND slot.status = :status "
        + "AND slot.startDateTime > :startDateTime "
        + "AND NOT EXISTS ("
        + "    SELECT item.id FROM BookingRequestItem item "
        + "    WHERE item.availabilitySlot.id = slot.id "
        + "    AND item.bookingRequest.active = true "
        + "    AND item.bookingRequest.status = :pendingStatus"
        + ") "
        + "ORDER BY slot.startDateTime ASC")
List<AvailabilitySlot> findAvailableSlotsVisibleToClient(
        @Param("professionalId") Long professionalId,
        @Param("status") AvailabilitySlotStatus status,
        @Param("startDateTime") Instant startDateTime,
        @Param("pendingStatus") BookingRequestStatus pendingStatus
);

    Optional<AvailabilitySlot> findByIdAndProfessional_IdAndActiveTrue(Long slotId, Long professionalId);

    Optional<AvailabilitySlot> findByIdAndActiveTrue(Long slotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slot FROM AvailabilitySlot slot WHERE slot.id = :slotId AND slot.active = true")
    Optional<AvailabilitySlot> findActiveByIdForUpdate(@Param("slotId") Long slotId);
}
