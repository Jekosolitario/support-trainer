package it.zuperman.support_trainer.availability.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import jakarta.persistence.LockModeType;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, Long> {

    boolean existsByProfessional_IdAndActiveTrueAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            Long professionalId,
            LocalDateTime endDateTime,
            LocalDateTime startDateTime
    );

    boolean existsByProfessional_IdAndActiveTrueAndIdNotAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
            Long professionalId,
            Long slotId,
            LocalDateTime endDateTime,
            LocalDateTime startDateTime
    );

    List<AvailabilitySlot> findAllByProfessional_IdAndActiveTrueOrderByStartDateTimeAsc(Long professionalId);

    List<AvailabilitySlot> findAllByProfessional_IdAndActiveTrueAndStatusAndStartDateTimeAfterOrderByStartDateTimeAsc(
            Long professionalId,
            AvailabilitySlotStatus status,
            LocalDateTime startDateTime
    );

    Optional<AvailabilitySlot> findByIdAndProfessional_IdAndActiveTrue(Long slotId, Long professionalId);

    Optional<AvailabilitySlot> findByIdAndActiveTrue(Long slotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slot FROM AvailabilitySlot slot WHERE slot.id = :slotId AND slot.active = true")
    Optional<AvailabilitySlot> findActiveByIdForUpdate(@Param("slotId") Long slotId);
}
