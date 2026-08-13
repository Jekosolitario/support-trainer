package it.zuperman.support_trainer.availability.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;

@Service
public class AvailabilityMaterializationService {

    static final Set<BookingRequestStatus> OCCUPYING_STATUSES = Set.of(
            BookingRequestStatus.PENDING,
            BookingRequestStatus.CONFIRMED
    );

    private final WeeklyAvailabilityRuleRepository weeklyRuleRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ApplicationTimeProvider timeProvider;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public AvailabilityMaterializationService(
            WeeklyAvailabilityRuleRepository weeklyRuleRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            ApplicationTimeProvider timeProvider,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager
    ) {
        this.weeklyRuleRepository = weeklyRuleRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.timeProvider = timeProvider;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "${app.time.business-zone:Europe/Rome}")
    public void synchronizeRollingHorizon() {
        LocalDate horizonEnd = timeProvider.todayBusiness().plusMonths(6);
        List<WeeklyAvailabilityRuleRepository.MaterializationCandidate> candidates
                = weeklyRuleRepository.findMaterializationCandidates(horizonEnd);
        candidates.forEach(candidate -> transactionTemplate.executeWithoutResult(status ->
                synchronizeRule(candidate.getRuleId(), candidate.getProfessionalId())));
    }

    @Transactional
    public void synchronizeProfessional(Long professionalId) {
        LocalDate horizonEnd = timeProvider.todayBusiness().plusMonths(6);
        weeklyRuleRepository.findMaterializationCandidatesForProfessional(professionalId, horizonEnd)
                .forEach(candidate -> synchronizeRule(candidate.getRuleId(), candidate.getProfessionalId()));
    }

    @Transactional
    public void synchronizeRule(Long ruleId, Long professionalId) {
        weeklyRuleRepository.lockProfessionalAvailability(professionalId)
                .orElseThrow(() -> new IllegalStateException("Professional not found while materializing"));

        Optional<WeeklyAvailabilityRule> currentRule = weeklyRuleRepository
                .findActiveOwnedByIdForUpdate(ruleId, professionalId);
        currentRule.ifPresent(rule -> {
            entityManager.refresh(rule, LockModeType.PESSIMISTIC_WRITE);
            rule.getAllowedDurations().size();
            synchronizeLockedRule(rule);
        });
    }

    private void synchronizeLockedRule(WeeklyAvailabilityRule rule) {
        LocalDate today = timeProvider.todayBusiness();
        LocalDate firstDate = rule.getValidFrom().isAfter(today) ? rule.getValidFrom() : today;
        LocalDate horizonEnd = today.plusMonths(6);
        LocalDate occurrenceDate = firstDate.with(
                java.time.temporal.TemporalAdjusters.nextOrSame(rule.getDayOfWeek())
        );

        while (!occurrenceDate.isAfter(horizonEnd)) {
            materializeOccurrence(rule, occurrenceDate);
            occurrenceDate = occurrenceDate.plusWeeks(1);
        }
    }

    private void materializeOccurrence(WeeklyAvailabilityRule rule, LocalDate date) {
        AvailabilityWindowPolicy.resolveWindow(
                date,
                rule.getStartTime(),
                rule.getEndTime(),
                timeProvider.businessZone()
        ).filter(window -> window.start().isAfter(timeProvider.nowInstant()))
                .ifPresent(window -> materializeWindow(rule, window));
    }

    private void materializeWindow(
            WeeklyAvailabilityRule rule,
            AvailabilityWindowPolicy.ConcreteWindow window
    ) {
        Optional<AvailabilitySlot> existing = availabilitySlotRepository
                .findByWeeklyRule_IdAndStartDateTime(rule.getId(), window.start());

        if (existing.isPresent()) {
            AvailabilitySlot slot = existing.get();
            slot.setEndDateTime(window.end());
            slot.setLocationLabel(rule.getLocationLabel());
            slot.setCapacity(rule.getCapacityPerSlot());
            slot.setActive(true);
            availabilitySlotRepository.save(slot);
            return;
        }

        boolean overlapsActiveSlot = availabilitySlotRepository
                .existsByProfessional_IdAndActiveTrueAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        rule.getProfessional().getId(),
                        window.end(),
                        window.start()
                );
        if (overlapsActiveSlot) {
            return;
        }

        availabilitySlotRepository.save(new AvailabilitySlot(
                rule.getProfessional(),
                rule,
                window.start(),
                window.end(),
                rule.getLocationLabel(),
                rule.getCapacityPerSlot()
        ));
    }
}
