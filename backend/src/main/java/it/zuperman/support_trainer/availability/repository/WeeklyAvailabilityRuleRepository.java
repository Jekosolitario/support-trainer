package it.zuperman.support_trainer.availability.repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import jakarta.persistence.LockModeType;

public interface WeeklyAvailabilityRuleRepository extends JpaRepository<WeeklyAvailabilityRule, Long> {

    interface MaterializationCandidate {

        Long getRuleId();

        Long getProfessionalId();
    }

    @Query(value = "SELECT id FROM professional_profiles WHERE id = :professionalId FOR UPDATE",
            nativeQuery = true)
    Optional<Long> lockProfessionalAvailability(@Param("professionalId") Long professionalId);

    @EntityGraph(attributePaths = "allowedDurations")
    List<WeeklyAvailabilityRule> findAllByProfessional_IdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(
            Long professionalId
    );

    @Query("SELECT rule.id AS ruleId, rule.professional.id AS professionalId "
            + "FROM WeeklyAvailabilityRule rule "
            + "WHERE rule.active = true "
            + "AND rule.validFrom <= :horizonEnd "
            + "ORDER BY rule.professional.id ASC, rule.id ASC")
    List<MaterializationCandidate> findMaterializationCandidates(
            @Param("horizonEnd") LocalDate horizonEnd
    );

    @Query("SELECT rule.id AS ruleId, rule.professional.id AS professionalId "
            + "FROM WeeklyAvailabilityRule rule "
            + "WHERE rule.professional.id = :professionalId "
            + "AND rule.active = true "
            + "AND rule.validFrom <= :horizonEnd "
            + "ORDER BY rule.id ASC")
    List<MaterializationCandidate> findMaterializationCandidatesForProfessional(
            @Param("professionalId") Long professionalId,
            @Param("horizonEnd") LocalDate horizonEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rule FROM WeeklyAvailabilityRule rule "
            + "WHERE rule.id = :ruleId "
            + "AND rule.professional.id = :professionalId "
            + "AND rule.active = true")
    Optional<WeeklyAvailabilityRule> findActiveOwnedByIdForUpdate(
            @Param("ruleId") Long ruleId,
            @Param("professionalId") Long professionalId
    );

    @Query("SELECT CASE WHEN COUNT(rule) > 0 THEN true ELSE false END "
            + "FROM WeeklyAvailabilityRule rule "
            + "WHERE rule.professional.id = :professionalId "
            + "AND rule.active = true "
            + "AND rule.dayOfWeek = :dayOfWeek "
            + "AND rule.startTime < :endTime "
            + "AND rule.endTime > :startTime")
    boolean existsOverlappingRule(
            @Param("professionalId") Long professionalId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Query("SELECT CASE WHEN COUNT(rule) > 0 THEN true ELSE false END "
            + "FROM WeeklyAvailabilityRule rule "
            + "WHERE rule.professional.id = :professionalId "
            + "AND rule.id <> :ruleId "
            + "AND rule.active = true "
            + "AND rule.dayOfWeek = :dayOfWeek "
            + "AND rule.startTime < :endTime "
            + "AND rule.endTime > :startTime")
    boolean existsOverlappingRuleExcludingCurrent(
            @Param("professionalId") Long professionalId,
            @Param("ruleId") Long ruleId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );
}
