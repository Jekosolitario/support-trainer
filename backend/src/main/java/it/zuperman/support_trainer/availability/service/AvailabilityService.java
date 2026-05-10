package it.zuperman.support_trainer.availability.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.availability.dto.request.CreateAvailabilitySlotRequest;
import it.zuperman.support_trainer.availability.dto.response.AvailabilitySlotResponse;
import it.zuperman.support_trainer.availability.entity.AvailabilitySlot;
import it.zuperman.support_trainer.availability.repository.AvailabilitySlotRepository;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.AvailabilitySlotStatus;
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

    public AvailabilityService(
            AvailabilitySlotRepository availabilitySlotRepository,
            UserRepository userRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            ProfessionalClientLinkRepository professionalClientLinkRepository
    ) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.userRepository = userRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
    }

    @Transactional
    public AvailabilitySlotResponse createAvailabilitySlot(CreateAvailabilitySlotRequest request) {
        ProfessionalProfile professional = getAuthenticatedProfessional();
        validateAvailabilitySpecialization(professional);
        validateTimeInterval(request.getStartDateTime(), request.getEndDateTime());
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

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getAvailableSlotsByProfessional(Long professionalId) {
        ClientProfile authenticatedClient = getAuthenticatedClient();

        ProfessionalProfile professional = getAccessibleProfessionalForClient(
                authenticatedClient.getId(),
                professionalId
        );

        validateAvailabilitySpecialization(professional);

        return availabilitySlotRepository
                .findAllByProfessional_IdAndActiveTrueAndStatusOrderByStartDateTimeAsc(
                        professionalId,
                        AvailabilitySlotStatus.AVAILABLE
                )
                .stream()
                .map(AvailabilitySlotResponse::fromEntity)
                .toList();
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
            if (!professionalProfile.getEmailVerified()) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "EMAIL_NOT_VERIFIED",
                        "Email non verificata"
                );
            }

            if (!professionalProfile.getActive()) {
                throw new AppException(
                        HttpStatus.FORBIDDEN,
                        "PROFESSIONAL_NOT_ACTIVE",
                        "Profilo professionista non attivo"
                );
            }

            if (user instanceof ClientProfile clientProfile) {
                if (!clientProfile.getActive()) {
                    throw new AppException(
                            HttpStatus.FORBIDDEN,
                            "CLIENT_NOT_ACTIVE",
                            "Profilo cliente non attivo"
                    );
                }
            }
        }
    }
}
