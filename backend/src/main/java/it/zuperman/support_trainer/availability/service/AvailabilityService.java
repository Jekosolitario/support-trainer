package it.zuperman.support_trainer.availability.service;

import java.time.LocalDateTime;
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

    public AvailabilityService(
            AvailabilitySlotRepository availabilitySlotRepository,
            UserRepository userRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            BookingRequestItemRepository bookingRequestItemRepository
    ) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.userRepository = userRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
        this.bookingRequestItemRepository = bookingRequestItemRepository;
    }

    @Transactional
    public AvailabilitySlotResponse createAvailabilitySlot(CreateAvailabilitySlotRequest request) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        professional = lockProfessionalForAvailabilityChange(professional.getId());

        validateTimeInterval(request.getStartDateTime(), request.getEndDateTime());
        validateSlotIsInFuture(request.getStartDateTime());

        validateNoOverlappingSlots(
                professional.getId(),
                request.getStartDateTime(),
                request.getEndDateTime()
        );

        AvailabilitySlot slot = new AvailabilitySlot(
                professional,
                request.getStartDateTime(),
                request.getEndDateTime()
        );

        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotResponse.fromEntity(savedSlot);
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getMyAvailabilitySlots() {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        return availabilitySlotRepository
                .findAllByProfessional_IdAndActiveTrueOrderByStartDateTimeAsc(professional.getId())
                .stream()
                .map(AvailabilitySlotResponse::fromEntity)
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
        validateUpdateRequestNotEmpty(request);

        LocalDateTime newStartDateTime = request.getStartDateTime() != null
                ? request.getStartDateTime()
                : slot.getStartDateTime();

        LocalDateTime newEndDateTime = request.getEndDateTime() != null
                ? request.getEndDateTime()
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
        return AvailabilitySlotResponse.fromEntity(savedSlot);
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
        return AvailabilitySlotResponse.fromEntity(savedSlot);
    }

    @Transactional
    public AvailabilitySlotResponse unblockAvailabilitySlot(Long slotId) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);

        AvailabilitySlot slot = getOwnedActiveSlot(slotId, professional.getId());

        if (slot.getStatus() != AvailabilitySlotStatus.BLOCKED) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "AVAILABILITY_SLOT_NOT_BLOCKED",
                    "Solo uno slot bloccato può essere sbloccato"
            );
        }

        slot.setStatus(AvailabilitySlotStatus.AVAILABLE);

        AvailabilitySlot savedSlot = availabilitySlotRepository.save(slot);
        return AvailabilitySlotResponse.fromEntity(savedSlot);
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
                .findAllByProfessional_IdAndActiveTrueAndStatusAndStartDateTimeAfterOrderByStartDateTimeAsc(
                        professionalId,
                        AvailabilitySlotStatus.AVAILABLE,
                        LocalDateTime.now()
                )
                .stream()
                .map(AvailabilitySlotResponse::fromEntity)
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

    private void validateTimeInterval(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (!startDateTime.isBefore(endDateTime)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TIME_INTERVAL",
                    "L'intervallo temporale non è valido"
            );
        }
    }

    private void validateSlotIsInFuture(LocalDateTime startDateTime) {
        if (!startDateTime.isAfter(LocalDateTime.now())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "AVAILABILITY_SLOT_IN_PAST",
                    "Lo slot disponibilità deve iniziare nel futuro"
            );
        }
    }

    private void validateNoOverlappingSlots(
            Long professionalId,
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
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
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
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

    private AvailabilitySlot getOwnedActiveSlot(Long slotId, Long professionalId) {
        return availabilitySlotRepository.findByIdAndProfessional_IdAndActiveTrue(slotId, professionalId)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "AVAILABILITY_SLOT_NOT_FOUND",
                "Slot disponibilità non trovato"
        ));
    }

    private AvailabilitySlot getOwnedActiveSlotForUpdate(Long slotId, Long professionalId) {
        AvailabilitySlot slot = availabilitySlotRepository.findActiveByIdForUpdate(slotId)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "AVAILABILITY_SLOT_NOT_FOUND",
                "Slot disponibilità non trovato"
        ));

        if (!slot.getProfessional().getId().equals(professionalId)) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "AVAILABILITY_SLOT_NOT_FOUND",
                    "Slot disponibilità non trovato"
            );
        }

        return slot;
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
        ProfessionalProfile professional = professionalProfileRepository.findById(professionalId)
                .orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "PROFESSIONAL_NOT_FOUND",
                "Professionista non trovato"
        ));

        if (!isReadableProfessional(professional)) {
            throw new AppException(
                    HttpStatus.NOT_FOUND,
                    "PROFESSIONAL_NOT_FOUND",
                    "Professionista non trovato"
            );
        }

        validateProfessionalAccess(clientId, professionalId);
        return professional;
    }

    private boolean isReadableProfessional(ProfessionalProfile professional) {
        return Boolean.TRUE.equals(professional.getActive())
                && professional.getAccountStatus() == AccountStatus.ACTIVE
                && Boolean.TRUE.equals(professional.getEmailVerified());
    }

    private void validateProfessionalAccess(Long clientId, Long professionalId) {
        boolean linked = professionalClientLinkRepository.existsByProfessional_IdAndClient_IdAndActiveTrue(
                professionalId,
                clientId
        );

        if (!linked) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "PROFESSIONAL_ACCESS_DENIED",
                    "Non puoi accedere a questo professionista"
            );
        }
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

        validateAuthenticatedUserAccess(user);
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

    private void validateAuthenticatedUserAccess(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_NOT_ACTIVE",
                    "Account non attivo"
            );
        }

        if (user instanceof ProfessionalProfile professionalProfile) {
            if (!Boolean.TRUE.equals(professionalProfile.getEmailVerified())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "EMAIL_NOT_VERIFIED",
                        "Email non verificata"
                );
            }

            if (!Boolean.TRUE.equals(professionalProfile.getActive())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "PROFESSIONAL_NOT_ACTIVE",
                        "Profilo professionista non attivo"
                );
            }
        }

        if (user instanceof ClientProfile clientProfile) {
            if (!Boolean.TRUE.equals(clientProfile.getActive())) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "CLIENT_NOT_ACTIVE",
                        "Profilo cliente non attivo"
                );
            }
        }
    }
}
