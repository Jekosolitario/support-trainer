package it.zuperman.support_trainer.availability.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;

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

    List<AvailabilitySlot> findAllByProfessional_IdAndActiveTrueAndStatusOrderByStartDateTimeAsc(
            Long professionalId,
            AvailabilitySlotStatus status
    );

    Optional<AvailabilitySlot> findByIdAndProfessional_IdAndActiveTrue(Long slotId, Long professionalId);

    Optional<AvailabilitySlot> findByIdAndActiveTrue(Long slotId);
}