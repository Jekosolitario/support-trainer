package it.zuperman.support_trainer.availability.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.dto.request.ChangeAvailabilitySlotBlockRequest;
import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.dto.response.ClientBookableOptionResponse;
import it.zuperman.support_trainer.availability.dto.response.ClientAvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlotChange;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotChangeRepository;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.availability.repository.WeeklyAvailabilityRuleRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.session.AuthenticatedUserLoader;

@Service
public class AvailabilityService {

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final AvailabilitySlotChangeRepository slotChangeRepository;
    private final AuthenticatedUserLoader authenticatedUserLoader;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;
    private final BookingRequestItemRepository bookingRequestItemRepository;
    private final ApplicationTimeProvider timeProvider;
    private final BusinessDateTimeMapper businessDateTimeMapper;
    private final UserReadinessValidator userReadinessValidator;
    private final WeeklyAvailabilityRuleRepository weeklyRuleRepository;
    private final AvailabilityMaterializationService materializationService;
    private final AvailabilityCapacityService capacityService;

    public AvailabilityService(
            AvailabilitySlotRepository availabilitySlotRepository,
            AvailabilitySlotChangeRepository slotChangeRepository,
            AuthenticatedUserLoader authenticatedUserLoader,
            ProfessionalProfileRepository professionalProfileRepository,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            BookingRequestItemRepository bookingRequestItemRepository,
            ApplicationTimeProvider timeProvider,
            BusinessDateTimeMapper businessDateTimeMapper,
            UserReadinessValidator userReadinessValidator,
            WeeklyAvailabilityRuleRepository weeklyRuleRepository,
            AvailabilityMaterializationService materializationService,
            AvailabilityCapacityService capacityService
    ) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.slotChangeRepository = slotChangeRepository;
        this.authenticatedUserLoader = authenticatedUserLoader;
        this.professionalProfileRepository = professionalProfileRepository;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
        this.bookingRequestItemRepository = bookingRequestItemRepository;
        this.timeProvider = timeProvider;
        this.businessDateTimeMapper = businessDateTimeMapper;
        this.userReadinessValidator = userReadinessValidator;
        this.weeklyRuleRepository = weeklyRuleRepository;
        this.materializationService = materializationService;
        this.capacityService = capacityService;
    }

    @Transactional
    public AvailabilitySlotResponse createAvailabilitySlot(CreateAvailabilitySlotRequest request) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);
        professional = lockProfessionalForAvailabilityChange(professional.getId());

        Instant startDateTime = businessDateTimeMapper.toInstant(request.getStartDateTime());
        Instant endDateTime = businessDateTimeMapper.toInstant(request.getEndDateTime());
        validateTimeInterval(startDateTime, endDateTime);
        validateSlotIsInFuture(startDateTime);
        validateNoOverlappingSlots(professional.getId(), startDateTime, endDateTime);

        AvailabilitySlot savedSlot = availabilitySlotRepository.save(new AvailabilitySlot(
                professional,
                startDateTime,
                endDateTime
        ));
        return toProfessionalSlotResponse(savedSlot);
    }

    @Transactional
    public List<AvailabilitySlotResponse> getMyAvailabilitySlots() {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        materializationService.synchronizeProfessional(professional.getId());

        List<AvailabilitySlot> slots = availabilitySlotRepository
                .findAllByProfessional_IdAndActiveTrueAndStartDateTimeAfterOrderByStartDateTimeAsc(
                        professional.getId(),
                        timeProvider.nowInstant()
                );
        AvailabilityCapacityService.OccupancySnapshot occupancy = capacityService.loadOccupancy(slots);
        return slots.stream()
                .map(slot -> toProfessionalSlotResponse(slot, occupancy))
                .toList();
    }

    @Transactional
    public AvailabilitySlotResponse updateAvailabilitySlot(Long slotId, UpdateAvailabilitySlotRequest request) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);
        professional = lockProfessionalForAvailabilityChange(professional.getId());

        AvailabilitySlot slot = getOwnedActiveSlotForUpdate(slotId, professional.getId());
        validateSlotCanBeUpdated(slot);
        validateSlotIsInFuture(slot.getStartDateTime());
        validateNoPendingBookingOnSlot(slot.getId());
        validateSlotHasNoBookingHistoryForReschedule(slot.getId());
        validateUpdateRequestNotEmpty(request);

        Instant newStartDateTime = request.getStartDateTime() != null
                ? businessDateTimeMapper.toInstant(request.getStartDateTime())
                : slot.getStartDateTime();
        Instant newEndDateTime = request.getEndDateTime() != null
                ? businessDateTimeMapper.toInstant(request.getEndDateTime())
                : slot.getEndDateTime();

        validateTimeInterval(newStartDateTime, newEndDateTime);
        validateSlotIsInFuture(newStartDateTime);
        validateNoOverlappingSlotsExcludingCurrent(
                professional.getId(),
                slot.getId(),
                newStartDateTime,
                newEndDateTime
        );

        slot.setStartDateTime(newStartDateTime);
        slot.setEndDateTime(newEndDateTime);
        return toProfessionalSlotResponse(availabilitySlotRepository.save(slot));
    }

    @Transactional
    public AvailabilitySlotResponse blockAvailabilitySlot(Long slotId) {
        return blockAvailabilitySlot(slotId, null);
    }

    @Transactional
    public AvailabilitySlotResponse blockAvailabilitySlot(
            Long slotId,
            ChangeAvailabilitySlotBlockRequest request
    ) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);
        AvailabilitySlot slot = getOwnedActiveSlotForUpdate(slotId, professional.getId());
        validateSlotIsInFuture(slot.getStartDateTime());

        if (Boolean.TRUE.equals(slot.getBlocked())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_AVAILABLE",
                    "Solo una finestra disponibile può essere bloccata"
            );
        }

        long impacted = capacityService.occupyingBookingCount(slot);
        String reason = validateAndNormalizeReason(
                request == null ? null : request.changeReason(),
                impacted
        );
        slot.setBlocked(true);
        slot.setStatus(AvailabilitySlotStatus.BLOCKED);
        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        slotChangeRepository.save(new AvailabilitySlotChange(
                savedSlot,
                AvailabilitySlotChange.ChangeType.BLOCK,
                reason,
                impacted
        ));
        return toProfessionalSlotResponse(savedSlot);
    }

    @Transactional
    public AvailabilitySlotResponse unblockAvailabilitySlot(Long slotId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);
        AvailabilitySlot slot = getOwnedActiveSlotForUpdate(slotId, professional.getId());
        validateSlotIsInFuture(slot.getStartDateTime());

        if (!Boolean.TRUE.equals(slot.getBlocked())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BLOCKED",
                    "Solo una finestra bloccata può essere sbloccata"
            );
        }

        slot.setBlocked(false);
        slot.setStatus(AvailabilitySlotStatus.AVAILABLE);
        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        slotChangeRepository.save(new AvailabilitySlotChange(
                savedSlot,
                AvailabilitySlotChange.ChangeType.UNBLOCK,
                null,
                0
        ));
        return toProfessionalSlotResponse(savedSlot);
    }

    @Transactional
    public List<ClientAvailabilitySlotResponse> getClientAvailableSlotsByProfessional(Long professionalId) {
        ClientProfile authenticatedClient = getAuthenticatedClient();
        ProfessionalProfile professional = getAccessibleProfessionalForClient(
                authenticatedClient.getId(),
                professionalId
        );
        validateAvailabilitySpecialization(professional);

        materializationService.synchronizeProfessional(professionalId);

        List<AvailabilitySlot> slots = availabilitySlotRepository
                .findFutureUnblockedSlotsVisibleToClient(professionalId, timeProvider.nowInstant());
        AvailabilityCapacityService.OccupancySnapshot occupancy = capacityService.loadOccupancy(slots);
        return slots.stream()
                .map(slot -> toClientSlotResponse(
                        slot,
                        capacityService.bookableOptionsForClient(
                                slot,
                                occupancy,
                                authenticatedClient.getId()
                        )
                ))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private ProfessionalProfile lockProfessionalForAvailabilityChange(Long professionalId) {
        weeklyRuleRepository.lockProfessionalAvailability(professionalId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "PROFESSIONAL_NOT_FOUND",
                        "Professionista non trovato"
                ));
        return professionalProfileRepository.findById(professionalId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "PROFESSIONAL_NOT_FOUND",
                        "Professionista non trovato"
                ));
    }

    private void validateAvailabilitySpecialization(ProfessionalProfile professional) {
        if (professional.getSpecialization() != ProfessionalSpecialization.PERSONAL_TRAINER) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "AVAILABILITY_SPECIALIZATION_NOT_ALLOWED",
                    "Il modulo availability è disponibile solo per i personal trainer"
            );
        }
    }

    private void validateTimeInterval(Instant startDateTime, Instant endDateTime) {
        if (!startDateTime.isBefore(endDateTime)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "L'intervallo temporale non è valido"
            );
        }
    }

    private void validateSlotIsInFuture(Instant startDateTime) {
        if (!startDateTime.isAfter(timeProvider.nowInstant())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "AVAILABILITY_SLOT_IN_PAST",
                    "La finestra di disponibilità deve iniziare nel futuro"
            );
        }
    }

    private void validateNoOverlappingSlots(Long professionalId, Instant start, Instant end) {
        if (availabilitySlotRepository
                .existsByProfessional_IdAndActiveTrueAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        professionalId, end, start
                )) {
            throw slotOverlap();
        }
    }

    private void validateNoOverlappingSlotsExcludingCurrent(
            Long professionalId,
            Long slotId,
            Instant start,
            Instant end
    ) {
        if (availabilitySlotRepository
                .existsByProfessional_IdAndActiveTrueAndIdNotAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        professionalId, slotId, end, start
                )) {
            throw slotOverlap();
        }
    }

    private AppException slotOverlap() {
        return new AppException(
                HttpStatus.CONFLICT,
                "AVAILABILITY_SLOT_OVERLAP",
                "Esiste già uno slot sovrapposto per questo professionista"
        );
    }

    private AvailabilitySlot getOwnedActiveSlotForUpdate(Long slotId, Long professionalId) {
        return availabilitySlotRepository.findActiveByIdAndProfessionalIdForUpdate(slotId, professionalId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "AVAILABILITY_SLOT_NOT_FOUND",
                        "Finestra di disponibilità non trovata"
                ));
    }

    private void validateNoPendingBookingOnSlot(Long slotId) {
        if (bookingRequestItemRepository
                .existsByAvailabilitySlot_IdAndBookingRequest_StatusAndBookingRequest_ActiveTrue(
                        slotId,
                        BookingRequestStatus.PENDING
                )) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_HAS_PENDING_BOOKING",
                    "Uno slot con una richiesta di prenotazione in attesa non può essere modificato o bloccato"
            );
        }
    }

    private void validateSlotHasNoBookingHistoryForReschedule(Long slotId) {
        if (bookingRequestItemRepository.existsByAvailabilitySlot_Id(slotId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_HAS_BOOKING_HISTORY",
                    "Uno slot già coinvolto in una richiesta di prenotazione non può essere ripianificato"
            );
        }
    }

    private void validateSlotCanBeUpdated(AvailabilitySlot slot) {
        if (slot.getWeeklyRule() != null) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "WEEKLY_AVAILABILITY_OCCURRENCE_NOT_PATCHABLE",
                    "Le occorrenze generate devono essere modificate dalla fascia settimanale"
            );
        }
        if (Boolean.TRUE.equals(slot.getBlocked())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_UPDATABLE",
                    "Solo una disponibilità non bloccata può essere aggiornata"
            );
        }
    }

    private void validateUpdateRequestNotEmpty(UpdateAvailabilitySlotRequest request) {
        if (request == null
                || request.getStartDateTime() == null && request.getEndDateTime() == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "AVAILABILITY_UPDATE_EMPTY",
                    "Devi indicare almeno una data/ora da aggiornare"
            );
        }
    }

    private String validateAndNormalizeReason(String value, long impactedBookings) {
        String normalized = value == null || value.trim().isEmpty() ? null : value.trim();
        if (normalized != null && normalized.length() > 1000) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    "La motivazione non può superare 1000 caratteri"
            );
        }
        if (impactedBookings > 0 && normalized == null) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_CHANGE_REASON_REQUIRED",
                    "Indica una motivazione perché il blocco coinvolge prenotazioni esistenti"
            );
        }
        return normalized;
    }

    private ClientProfile getAuthenticatedClient() {
        User user = getAuthenticatedUser();
        if (!(user instanceof ClientProfile clientProfile)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ROLE_NOT_ALLOWED",
                    "Solo il cliente può accedere a questa risorsa"
            );
        }
        return clientProfile;
    }

    private ProfessionalProfile getAccessibleProfessionalForClient(Long clientId, Long professionalId) {
        return professionalClientLinkRepository.findAccessibleProfessional(
                clientId,
                professionalId,
                AccountStatus.ACTIVE
        ).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "PROFESSIONAL_NOT_FOUND",
                "Professionista non trovato"
        ));
    }

    private ProfessionalProfile getAuthenticatedProfessional() {
        User user = getAuthenticatedUser();
        if (!(user instanceof ProfessionalProfile professionalProfile)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ROLE_NOT_ALLOWED",
                    "Solo il professionista può accedere a questa risorsa"
            );
        }
        return professionalProfile;
    }

    private User getAuthenticatedUser() {
        User user = authenticatedUserLoader.requireAuthenticatedUser();
        userReadinessValidator.validateOperationalUser(user);
        return user;
    }

    private AvailabilitySlotResponse toProfessionalSlotResponse(AvailabilitySlot slot) {
        return toProfessionalSlotResponse(slot, capacityService.loadOccupancy(List.of(slot)));
    }

    private AvailabilitySlotResponse toProfessionalSlotResponse(
            AvailabilitySlot slot,
            AvailabilityCapacityService.OccupancySnapshot occupancy
    ) {
        long maximumOccupancy = capacityService.maximumOccupancy(slot, occupancy);
        return AvailabilitySlotResponse.fromEntity(
                slot,
                maximumOccupancy,
                capacityService.hasAnyBookableCombination(slot, occupancy),
                businessDateTimeMapper
        );
    }

    private ClientAvailabilitySlotResponse toClientSlotResponse(
            AvailabilitySlot slot,
            List<AvailabilityCapacityService.BookableOption> options
    ) {
        if (options.isEmpty()) {
            return null;
        }
        return new ClientAvailabilitySlotResponse(
                slot.getId(),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getStartDateTime()),
                businessDateTimeMapper.toBusinessOffsetDateTime(slot.getEndDateTime()),
                AvailabilityWindowPolicy.allowedDurations(slot),
                AvailabilityWindowPolicy.START_INTERVAL_MINUTES,
                slot.getLocationLabel(),
                slot.getCapacity(),
                options.stream()
                        .map(option -> new ClientBookableOptionResponse(
                        option.startDateTime(),
                        option.allowedDurations()
                ))
                        .toList()
        );
    }
}
