package it.zuperman.support_trainer.availability.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.common.enums.AccountStatus;
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

    @EntityGraph(attributePaths = {"weeklyRule", "weeklyRule.allowedDurations"})
    List<AvailabilitySlot> findAllByProfessional_IdAndActiveTrueAndStartDateTimeAfterOrderByStartDateTimeAsc(
            Long professionalId,
            Instant now
    );

    Optional<AvailabilitySlot> findByWeeklyRule_IdAndStartDateTime(Long weeklyRuleId, Instant startDateTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slot FROM AvailabilitySlot slot "
            + "WHERE slot.weeklyRule.id = :weeklyRuleId "
            + "AND slot.startDateTime >= :effectiveFrom "
            + "ORDER BY slot.startDateTime ASC")
    List<AvailabilitySlot> findRuleSlotsFromForUpdate(
            @Param("weeklyRuleId") Long weeklyRuleId,
            @Param("effectiveFrom") Instant effectiveFrom
    );

    @Query("SELECT DISTINCT slot FROM AvailabilitySlot slot "
            + "JOIN FETCH slot.weeklyRule rule "
            + "JOIN FETCH rule.allowedDurations "
            + "WHERE slot.professional.id = :professionalId "
            + "AND slot.active = true "
            + "AND slot.blocked = false "
            + "AND slot.weeklyRule IS NOT NULL "
            + "AND slot.startDateTime > :startDateTime "
            + "ORDER BY slot.startDateTime ASC")
    List<AvailabilitySlot> findFutureUnblockedSlotsVisibleToClient(
            @Param("professionalId") Long professionalId,
            @Param("startDateTime") Instant startDateTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slot FROM AvailabilitySlot slot "
            + "WHERE slot.id = :slotId "
            + "AND slot.professional.id = :professionalId "
            + "AND slot.active = true")
    Optional<AvailabilitySlot> findActiveByIdAndProfessionalIdForUpdate(
            @Param("slotId") Long slotId,
            @Param("professionalId") Long professionalId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slot FROM AvailabilitySlot slot "
            + "WHERE slot.id = :slotId "
            + "AND slot.active = true "
            + "AND slot.weeklyRule IS NOT NULL "
            + "AND slot.professional.active = true "
            + "AND slot.professional.accountStatus = :accountStatus "
            + "AND slot.professional.emailVerified = true "
            + "AND EXISTS ("
            + "SELECT link.id FROM ProfessionalClientLink link "
            + "WHERE link.professional.id = slot.professional.id "
            + "AND link.client.id = :clientId "
            + "AND link.active = true"
            + ")")
    Optional<AvailabilitySlot> findActiveAccessibleByIdAndClientIdForUpdate(
            @Param("slotId") Long slotId,
            @Param("clientId") Long clientId,
            @Param("accountStatus") AccountStatus accountStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slot FROM AvailabilitySlot slot WHERE slot.id = :slotId AND slot.active = true")
    Optional<AvailabilitySlot> findActiveByIdForUpdate(@Param("slotId") Long slotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT slot FROM AvailabilitySlot slot WHERE slot.id = :slotId")
    Optional<AvailabilitySlot> findByIdForUpdate(@Param("slotId") Long slotId);
}
