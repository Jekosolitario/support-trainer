package it.zuperman.support_trainer.availability.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.request.UpdateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.booking.repository.BookingRequestItemRepository;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
import it.zuperman.support_trainer.common.enums.BookingRequestStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.BusinessDateTimeMapper;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

@Service
public class AvailabilityService {

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;
    private final BookingRequestItemRepository bookingRequestItemRepository;
    private final ApplicationTimeProvider timeProvider;
    private final BusinessDateTimeMapper businessDateTimeMapper;
    private final UserReadinessValidator userReadinessValidator;

    public AvailabilityService(
            AvailabilitySlotRepository availabilitySlotRepository,
            UserRepository userRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            BookingRequestItemRepository bookingRequestItemRepository,
            ApplicationTimeProvider timeProvider,
            BusinessDateTimeMapper businessDateTimeMapper,
            UserReadinessValidator userReadinessValidator
    ) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.userRepository = userRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
        this.bookingRequestItemRepository = bookingRequestItemRepository;
        this.timeProvider = timeProvider;
        this.businessDateTimeMapper = businessDateTimeMapper;
        this.userReadinessValidator = userReadinessValidator;
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

        validateNoOverlappingSlots(
                professional.getId(),
                startDateTime,
                endDateTime
        );

        AvailabilitySlot slot = new AvailabilitySlot(
                professional,
                startDateTime,
                endDateTime
        );

        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotResponse.fromEntity(savedSlot, businessDateTimeMapper);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getMyAvailabilitySlots() {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        return availabilitySlotRepository
                .findAllByProfessional_IdAndActiveTrueOrderByStartDateTimeAsc(professional.getId())
                .stream()
                .map(slot -> AvailabilitySlotResponse.fromEntity(slot, businessDateTimeMapper))
                .toList();
    }

    @Transactional
    public AvailabilitySlotResponse updateAvailabilitySlot(Long slotId, UpdateAvailabilitySlotRequest request) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        professional = lockProfessionalForAvailabilityChange(professional.getId());

        AvailabilitySlot slot = getOwnedActiveSlotForUpdate(slotId, professional.getId());

        validateSlotCanBeUpdated(slot);
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

        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotResponse.fromEntity(savedSlot, businessDateTimeMapper);
    }

    @Transactional
    public AvailabilitySlotResponse blockAvailabilitySlot(Long slotId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        AvailabilitySlot slot = getOwnedActiveSlotForUpdate(slotId, professional.getId());

        if (slot.getStatus() != AvailabilitySlotStatus.AVAILABLE) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_AVAILABLE",
                    "Solo uno slot disponibile può essere bloccato"
            );
        }

        validateNoPendingBookingOnSlot(slot.getId());

        slot.setStatus(AvailabilitySlotStatus.BLOCKED);

        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotResponse.fromEntity(savedSlot, businessDateTimeMapper);
    }

    @Transactional
    public AvailabilitySlotResponse unblockAvailabilitySlot(Long slotId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        AvailabilitySlot slot = getOwnedActiveSlotForUpdate(slotId, professional.getId());

        if (slot.getStatus() != AvailabilitySlotStatus.BLOCKED) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BLOCKED",
                    "Solo uno slot bloccato può essere sbloccato"
            );
        }

        slot.setStatus(AvailabilitySlotStatus.AVAILABLE);

        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotResponse.fromEntity(savedSlot, businessDateTimeMapper);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getAvailableSlotsByProfessional(Long professionalId) {
        ClientProfile authenticatedClient = getAuthenticatedClient();

        ProfessionalProfile professional = getAccessibleProfessionalForClient(
                authenticatedClient.getId(),
                professionalId
        );

        validateAvailabilitySpecialization(professional);

        return availabilitySlotRepository
                .findAvailableSlotsVisibleToClient(
                        professionalId,
                        AvailabilitySlotStatus.AVAILABLE,
                        timeProvider.nowInstant(),
                        BookingRequestStatus.PENDING
                )
                .stream()
                .map(slot -> AvailabilitySlotResponse.fromEntity(slot, businessDateTimeMapper))
                .toList();
    }

    private ProfessionalProfile lockProfessionalForAvailabilityChange(Long professionalId) {
        return professionalProfileRepository.findByIdForUpdate(professionalId)
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
                    "Lo slot disponibilità deve iniziare nel futuro"
            );
        }
    }

    private void validateNoOverlappingSlots(
            Long professionalId,
            Instant startDateTime,
            Instant endDateTime
    ) {
        boolean overlapping = availabilitySlotRepository
                .existsByProfessional_IdAndActiveTrueAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        professionalId,
                        endDateTime,
                        startDateTime
                );

        if (overlapping) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_OVERLAP",
                    "Esiste già uno slot sovrapposto per questo professionista"
            );
        }
    }

    private void validateNoOverlappingSlotsExcludingCurrent(
            Long professionalId,
            Long slotId,
            Instant startDateTime,
            Instant endDateTime
    ) {
        boolean overlapping = availabilitySlotRepository
                .existsByProfessional_IdAndActiveTrueAndIdNotAndStartDateTimeLessThanAndEndDateTimeGreaterThan(
                        professionalId,
                        slotId,
                        endDateTime,
                        startDateTime
                );

        if (overlapping) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_OVERLAP",
                    "Esiste già uno slot sovrapposto per questo professionista"
            );
        }
    }

    private AvailabilitySlot getOwnedActiveSlotForUpdate(Long slotId, Long professionalId) {
        return availabilitySlotRepository.findActiveByIdAndProfessionalIdForUpdate(slotId, professionalId)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "AVAILABILITY_SLOT_NOT_FOUND",
                "Slot disponibilità non trovato"
        ));
    }

    private void validateNoPendingBookingOnSlot(Long slotId) {
        boolean hasPendingBooking = bookingRequestItemRepository
                .existsByAvailabilitySlot_IdAndBookingRequest_StatusAndBookingRequest_ActiveTrue(
                        slotId,
                        BookingRequestStatus.PENDING
                );

        if (hasPendingBooking) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_HAS_PENDING_BOOKING",
                    "Uno slot con una richiesta di prenotazione in attesa non può essere modificato o bloccato"
            );
        }
    }

    private void validateSlotHasNoBookingHistoryForReschedule(Long slotId) {
        boolean hasBookingHistory = bookingRequestItemRepository
                .existsByAvailabilitySlot_Id(slotId);

        if (hasBookingHistory) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_HAS_BOOKING_HISTORY",
                    "Uno slot già coinvolto in una richiesta di prenotazione non può essere ripianificato"
            );
        }
    }

    private void validateSlotCanBeUpdated(AvailabilitySlot slot) {
        if (slot.getStatus() != AvailabilitySlotStatus.AVAILABLE) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_UPDATABLE",
                    "Solo uno slot disponibile può essere aggiornato"
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
        String email = getAuthenticatedEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATED_USER_NOT_FOUND",
                "Utente autenticato non trovato"
        ));

        userReadinessValidator.validateOperationalUser(user);
        return user;
    }

    private String getAuthenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            throw new AppException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "Utente non autenticato"
            );
        }

        return authentication.getName().trim().toLowerCase();
    }

}
