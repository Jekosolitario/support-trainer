package it.zuperman.support_trainer.availability.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.dto.request.CreateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.DeactivateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateWeeklyAvailabilityRuleRequest;
import it.zuperman.support_trainer.availability.dto.response.WeeklyAvailabilityRuleImpactResponse;
import it.zuperman.support_trainer.availability.dto.response.WeeklyAvailabilityRuleResponse;
import it.zuperman.support_trainer.availability.entity.AvailabilityRuleChange;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.WeeklyAvailabilityRule;
import it.zuperman.support_trainer.availability.repository.AvailabilityRuleChangeRepository;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.security.session.AuthenticatedUserLoader;

@Service
public class WeeklyAvailabilityRuleService {

    private final WeeklyAvailabilityRuleRepository weeklyRuleRepository;
    private final AvailabilityRuleChangeRepository ruleChangeRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final BookingRequestItemRepository bookingRequestItemRepository;
    private final AvailabilityMaterializationService materializationService;
    private final AvailabilityCapacityService capacityService;
    private final AuthenticatedUserLoader authenticatedUserLoader;
    private final UserReadinessValidator userReadinessValidator;
    private final ApplicationTimeProvider timeProvider;

    public WeeklyAvailabilityRuleService(
            WeeklyAvailabilityRuleRepository weeklyRuleRepository,
            AvailabilityRuleChangeRepository ruleChangeRepository,
            AvailabilitySlotRepository availabilitySlotRepository,
            BookingRequestItemRepository bookingRequestItemRepository,
            AvailabilityMaterializationService materializationService,
            AvailabilityCapacityService capacityService,
            AuthenticatedUserLoader authenticatedUserLoader,
            UserReadinessValidator userReadinessValidator,
            ApplicationTimeProvider timeProvider
    ) {
        this.weeklyRuleRepository = weeklyRuleRepository;
        this.ruleChangeRepository = ruleChangeRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.bookingRequestItemRepository = bookingRequestItemRepository;
        this.materializationService = materializationService;
        this.capacityService = capacityService;
        this.authenticatedUserLoader = authenticatedUserLoader;
        this.userReadinessValidator = userReadinessValidator;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public WeeklyAvailabilityRuleResponse create(CreateWeeklyAvailabilityRuleRequest request) {
        ProfessionalProfile professional = lockAuthenticatedPersonalTrainer();
        Set<Integer> allowedDurations = validateRuleValues(
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                request.allowedDurations(),
                request.capacityPerSlot(),
                request.locationLabel()
        );
        validateCreateValidFrom(request.validFrom());
        validateNoOverlap(
                professional.getId(),
                null,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime()
        );

        WeeklyAvailabilityRule rule = new WeeklyAvailabilityRule(
                professional,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                allowedDurations,
                normalizeOptionalText(request.locationLabel()),
                request.capacityPerSlot(),
                request.validFrom()
        );
        WeeklyAvailabilityRule saved = weeklyRuleRepository.saveAndFlush(rule);
        materializationService.synchronizeRule(saved.getId(), professional.getId());
        return WeeklyAvailabilityRuleResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<WeeklyAvailabilityRuleResponse> listMine() {
        ProfessionalProfile professional = getAuthenticatedPersonalTrainer();
        List<WeeklyAvailabilityRule> rules = weeklyRuleRepository
                .findAllByProfessional_IdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(professional.getId());
        return rules.stream().map(WeeklyAvailabilityRuleResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public WeeklyAvailabilityRuleImpactResponse previewImpact(Long ruleId) {
        ProfessionalProfile professional = getAuthenticatedPersonalTrainer();
        WeeklyAvailabilityRule rule = weeklyRuleRepository.findById(ruleId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .filter(candidate -> candidate.getProfessional().getId().equals(professional.getId()))
                .orElseThrow(this::ruleNotFound);
        long impacted = impactedBookings(rule, timeProvider.nowInstant());
        return new WeeklyAvailabilityRuleImpactResponse(impacted > 0, impacted, impacted > 0);
    }

    @Transactional
    public WeeklyAvailabilityRuleResponse update(Long ruleId, UpdateWeeklyAvailabilityRuleRequest request) {
        ProfessionalProfile professional = lockAuthenticatedPersonalTrainer();
        Set<Integer> allowedDurations = validateRuleValues(
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                request.allowedDurations(),
                request.capacityPerSlot(),
                request.locationLabel()
        );

        WeeklyAvailabilityRule rule = getOwnedRuleForUpdate(ruleId, professional.getId());
        validateNoOverlap(
                professional.getId(),
                ruleId,
                request.dayOfWeek(),
                request.startTime(),
                request.endTime()
        );

        Instant now = timeProvider.nowInstant();
        List<AvailabilitySlot> affectedSlots = lockAffectedSlots(rule, now);
        long impacted = impactedBookings(rule, now);
        String reason = validateAndNormalizeReason(request.changeReason(), impacted);
        validateCapacityForImpactedSlots(affectedSlots, request.capacityPerSlot());
        deactivateMaterializedSlots(affectedSlots);

        rule.replaceSchedule(
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                allowedDurations,
                normalizeOptionalText(request.locationLabel()),
                request.capacityPerSlot()
        );
        WeeklyAvailabilityRule saved = weeklyRuleRepository.saveAndFlush(rule);
        ruleChangeRepository.save(new AvailabilityRuleChange(
                saved,
                timeProvider.todayBusiness(),
                AvailabilityRuleChange.ChangeType.UPDATE,
                reason,
                impacted
        ));
        materializationService.synchronizeRule(saved.getId(), professional.getId());
        return WeeklyAvailabilityRuleResponse.fromEntity(saved);
    }

    @Transactional
    public void deactivate(Long ruleId, DeactivateWeeklyAvailabilityRuleRequest request) {
        ProfessionalProfile professional = lockAuthenticatedPersonalTrainer();
        WeeklyAvailabilityRule rule = getOwnedRuleForUpdate(ruleId, professional.getId());
        Instant now = timeProvider.nowInstant();
        List<AvailabilitySlot> affectedSlots = lockAffectedSlots(rule, now);
        long impacted = impactedBookings(rule, now);
        String reason = validateAndNormalizeReason(request.changeReason(), impacted);
        deactivateMaterializedSlots(affectedSlots);
        rule.deactivate();
        weeklyRuleRepository.save(rule);
        ruleChangeRepository.save(new AvailabilityRuleChange(
                rule,
                timeProvider.todayBusiness(),
                AvailabilityRuleChange.ChangeType.DEACTIVATE,
                reason,
                impacted
        ));
    }

    private Set<Integer> validateRuleValues(
            java.time.DayOfWeek dayOfWeek,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            List<Integer> durations,
            Integer capacity,
            String locationLabel
    ) {
        if (dayOfWeek == null) {
            throw validationError("Il giorno della settimana è obbligatorio");
        }
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw validationError("L'orario di inizio deve precedere quello di fine");
        }
        if (!AvailabilityWindowPolicy.isAligned(startTime)
                || !AvailabilityWindowPolicy.isAligned(endTime)) {
            throw validationError("Gli orari devono essere allineati a intervalli di 15 minuti");
        }
        if (durations == null || durations.isEmpty()) {
            throw validationError("Seleziona almeno una durata");
        }
        if (durations.size() > 12 || durations.stream().anyMatch(java.util.Objects::isNull)) {
            throw validationError("Le durate indicate non sono valide");
        }
        long windowMinutes = Duration.between(startTime, endTime).toMinutes();
        LinkedHashSet<Integer> normalized = durations.stream()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (normalized.size() != durations.size()) {
            throw validationError("Le durate non possono essere duplicate");
        }
        if (normalized.stream().anyMatch(duration -> !AvailabilityWindowPolicy.isAllowedDuration(duration))) {
            throw validationError("Le durate devono essere comprese tra 15 e 180 minuti e multiple di 15");
        }
        if (normalized.stream().anyMatch(duration -> duration > windowMinutes)) {
            throw validationError("Ogni durata deve rientrare interamente nella fascia");
        }
        if (capacity == null || capacity < 1) {
            throw validationError("La capacità deve essere almeno 1");
        }
        if (locationLabel != null && locationLabel.trim().length() > 255) {
            throw validationError("Il luogo non può superare 255 caratteri");
        }
        return normalized;
    }

    private void validateCreateValidFrom(LocalDate validFrom) {
        if (validFrom == null || validFrom.isBefore(timeProvider.todayBusiness())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "AVAILABILITY_EFFECTIVE_DATE_IN_PAST",
                    "La fascia deve essere valida da oggi o da una data futura"
            );
        }
    }

    private void validateNoOverlap(
            Long professionalId,
            Long excludedRuleId,
            java.time.DayOfWeek dayOfWeek,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime
    ) {
        boolean overlap = excludedRuleId == null
                ? weeklyRuleRepository.existsOverlappingRule(
                        professionalId, dayOfWeek, startTime, endTime
                )
                : weeklyRuleRepository.existsOverlappingRuleExcludingCurrent(
                        professionalId, excludedRuleId, dayOfWeek, startTime, endTime
                );
        if (overlap) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "WEEKLY_AVAILABILITY_RULE_OVERLAP",
                    "Esiste già una fascia sovrapposta nello stesso giorno"
            );
        }
    }

    private void validateCapacityForImpactedSlots(
            List<AvailabilitySlot> affectedSlots,
            int newCapacity
    ) {
        AvailabilityCapacityService.OccupancySnapshot occupancy
                = capacityService.loadOccupancy(affectedSlots);
        for (AvailabilitySlot slot : affectedSlots) {
            if (capacityService.maximumOccupancy(slot, occupancy) > newCapacity) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "AVAILABILITY_CAPACITY_BELOW_OCCUPANCY",
                        "La capacità non può essere inferiore all'occupazione esistente"
                );
            }
        }
    }

    private void deactivateMaterializedSlots(List<AvailabilitySlot> affectedSlots) {
        affectedSlots.forEach(slot -> slot.setActive(false));
        availabilitySlotRepository.saveAll(affectedSlots);
    }

    private List<AvailabilitySlot> lockAffectedSlots(WeeklyAvailabilityRule rule, Instant now) {
        return availabilitySlotRepository.findRuleSlotsFromForUpdate(rule.getId(), now);
    }

    private long impactedBookings(WeeklyAvailabilityRule rule, Instant now) {
        return bookingRequestItemRepository.countImpactedBookings(
                rule.getId(),
                now,
                AvailabilityMaterializationService.OCCUPYING_STATUSES
        );
    }

    private String validateAndNormalizeReason(String value, long impactedBookings) {
        String normalized = normalizeOptionalText(value);
        if (normalized != null && normalized.length() > 1000) {
            throw validationError("La motivazione non può superare 1000 caratteri");
        }
        if (impactedBookings > 0 && normalized == null) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_CHANGE_REASON_REQUIRED",
                    "Indica una motivazione perché la modifica coinvolge prenotazioni esistenti"
            );
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private WeeklyAvailabilityRule getOwnedRuleForUpdate(Long ruleId, Long professionalId) {
        return weeklyRuleRepository.findActiveOwnedByIdForUpdate(ruleId, professionalId)
                .orElseThrow(this::ruleNotFound);
    }

    private ProfessionalProfile lockAuthenticatedPersonalTrainer() {
        ProfessionalProfile professional = getAuthenticatedPersonalTrainer();
        weeklyRuleRepository.lockProfessionalAvailability(professional.getId())
                .orElseThrow(this::ruleNotFound);
        return professional;
    }

    private ProfessionalProfile getAuthenticatedPersonalTrainer() {
        User user = authenticatedUserLoader.requireAuthenticatedUser();
        userReadinessValidator.validateOperationalUser(user);
        if (!(user instanceof ProfessionalProfile professional)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ROLE_NOT_ALLOWED",
                    "Solo il professionista può accedere a questa risorsa"
            );
        }
        if (professional.getSpecialization() != ProfessionalSpecialization.PERSONAL_TRAINER) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "AVAILABILITY_SPECIALIZATION_NOT_ALLOWED",
                    "Il modulo availability è disponibile solo per i personal trainer"
            );
        }
        return professional;
    }

    private AppException validationError(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private AppException ruleNotFound() {
        return new AppException(
                HttpStatus.NOT_FOUND,
                "WEEKLY_AVAILABILITY_RULE_NOT_FOUND",
                "Fascia settimanale non trovata"
        );
    }
}
