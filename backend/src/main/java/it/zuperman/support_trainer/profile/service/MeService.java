package it.zuperman.support_trainer.profile.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.ClientOperationalStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalOperationalStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.profile.dto.request.UpdateMyProfileRequest;
import it.zuperman.support_trainer.profile.dto.request.UpdateOperationalStatusRequest;
import it.zuperman.support_trainer.profile.dto.response.MyAccountResponse;
import it.zuperman.support_trainer.profile.dto.response.MyProfileResponse;

@Service
public class MeService {

    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final ClientProfileRepository clientProfileRepository;

    public MeService(
            UserRepository userRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            ClientProfileRepository clientProfileRepository
    ) {
        this.userRepository = userRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
    }

    @Transactional(readOnly = true)
    public MyProfileResponse getMyProfile() {
        User user = getAuthenticatedUser();

        if (user instanceof ProfessionalProfile professionalProfile) {
            return MyProfileResponse.fromProfessional(professionalProfile);
        }

        if (user instanceof ClientProfile clientProfile) {
            return MyProfileResponse.fromClient(clientProfile);
        }

        throw new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "UNSUPPORTED_USER_TYPE",
                "Tipo utente non supportato"
        );
    }

    @Transactional(readOnly = true)
    public MyAccountResponse getMyAccount() {
        User user = getAuthenticatedUser();
        return MyAccountResponse.fromUser(user);
    }

    @Transactional
    public MyProfileResponse updateMyProfile(UpdateMyProfileRequest request) {
        User user = getAuthenticatedUser();

        if (request.getFirstName() != null) {
            user.setFirstName(normalizeRequiredText(request.getFirstName(), "Il nome non può essere vuoto"));
        }

        if (request.getLastName() != null) {
            user.setLastName(normalizeRequiredText(request.getLastName(), "Il cognome non può essere vuoto"));
        }

        if (user instanceof ProfessionalProfile professionalProfile) {
            validateNoClientOnlyFields(request);
            applyProfessionalProfileUpdates(professionalProfile, request);
            ProfessionalProfile savedProfessional = professionalProfileRepository.save(professionalProfile);
            return MyProfileResponse.fromProfessional(savedProfessional);
        }

        if (user instanceof ClientProfile clientProfile) {
            validateNoProfessionalOnlyFields(request);
            applyClientProfileUpdates(clientProfile, request);
            ClientProfile savedClient = clientProfileRepository.save(clientProfile);
            return MyProfileResponse.fromClient(savedClient);
        }

        throw new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "UNSUPPORTED_USER_TYPE",
                "Tipo utente non supportato"
        );
    }

    private void validateNoClientOnlyFields(UpdateMyProfileRequest request) {
        if (request.getBirthDate() != null
                || request.getHeightCm() != null
                || request.getPrimaryGoal() != null
                || request.getGender() != null
                || request.getMedicalNotes() != null
                || request.getInjuryNotes() != null
                || request.getNotes() != null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_FIELDS_NOT_ALLOWED",
                    "Questi campi non sono modificabili per un professionista"
            );
        }
    }

    private void validateNoProfessionalOnlyFields(UpdateMyProfileRequest request) {
        if (request.getPhoneNumber() != null
                || request.getBio() != null
                || request.getWorkplaceName() != null
                || request.getCity() != null
                || request.getInstagramUrl() != null
                || request.getWebsiteUrl() != null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "PROFILE_FIELDS_NOT_ALLOWED",
                    "Questi campi non sono modificabili per un cliente"
            );
        }
    }

    @Transactional
    public MyProfileResponse updateMyOperationalStatus(UpdateOperationalStatusRequest request) {
        User user = getAuthenticatedUser();
        String rawOperationalStatus = normalizeRequiredText(
                request.getOperationalStatus(),
                "Lo stato operativo è obbligatorio"
        );

        if (user instanceof ProfessionalProfile professionalProfile) {
            professionalProfile.setOperationalStatus(parseProfessionalOperationalStatus(rawOperationalStatus));
            ProfessionalProfile savedProfessional = professionalProfileRepository.save(professionalProfile);
            return MyProfileResponse.fromProfessional(savedProfessional);
        }

        if (user instanceof ClientProfile clientProfile) {
            clientProfile.setOperationalStatus(parseClientOperationalStatus(rawOperationalStatus));
            ClientProfile savedClient = clientProfileRepository.save(clientProfile);
            return MyProfileResponse.fromClient(savedClient);
        }

        throw new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "UNSUPPORTED_USER_TYPE",
                "Tipo utente non supportato"
        );
    }

    private void applyProfessionalProfileUpdates(
            ProfessionalProfile professionalProfile,
            UpdateMyProfileRequest request
    ) {
        if (request.getPhoneNumber() != null) {
            professionalProfile.setPhoneNumber(normalizeOptionalText(request.getPhoneNumber()));
        }

        if (request.getBio() != null) {
            professionalProfile.setBio(normalizeOptionalText(request.getBio()));
        }

        if (request.getWorkplaceName() != null) {
            professionalProfile.setWorkplaceName(normalizeOptionalText(request.getWorkplaceName()));
        }

        if (request.getCity() != null) {
            professionalProfile.setCity(normalizeOptionalText(request.getCity()));
        }

        if (request.getInstagramUrl() != null) {
            professionalProfile.setInstagramUrl(normalizeOptionalText(request.getInstagramUrl()));
        }

        if (request.getWebsiteUrl() != null) {
            professionalProfile.setWebsiteUrl(normalizeOptionalText(request.getWebsiteUrl()));
        }
    }

    private void applyClientProfileUpdates(
            ClientProfile clientProfile,
            UpdateMyProfileRequest request
    ) {
        if (request.getBirthDate() != null) {
            clientProfile.setBirthDate(request.getBirthDate());
        }

        if (request.getHeightCm() != null) {
            clientProfile.setHeightCm(request.getHeightCm());
        }

        if (request.getPrimaryGoal() != null) {
            clientProfile.setPrimaryGoal(
                    normalizeRequiredText(request.getPrimaryGoal(), "L'obiettivo principale non può essere vuoto")
            );
        }

        if (request.getGender() != null) {
            clientProfile.setGender(request.getGender());
        }

        if (request.getMedicalNotes() != null) {
            clientProfile.setMedicalNotes(normalizeOptionalText(request.getMedicalNotes()));
        }

        if (request.getInjuryNotes() != null) {
            clientProfile.setInjuryNotes(normalizeOptionalText(request.getInjuryNotes()));
        }

        if (request.getNotes() != null) {
            clientProfile.setNotes(normalizeOptionalText(request.getNotes()));
        }
    }

    private User getAuthenticatedUser() {
        String email = getAuthenticatedEmail();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATED_USER_NOT_FOUND",
                        "Utente autenticato non trovato"
                ));
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

    private ProfessionalOperationalStatus parseProfessionalOperationalStatus(String value) {
        try {
            return ProfessionalOperationalStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OPERATIONAL_STATUS",
                    "Stato operativo professionista non valido"
            );
        }
    }

    private ClientOperationalStatus parseClientOperationalStatus(String value) {
        try {
            return ClientOperationalStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OPERATIONAL_STATUS",
                    "Stato operativo cliente non valido"
            );
        }
    }

    private String normalizeRequiredText(String value, String errorMessage) {
        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    errorMessage
            );
        }

        return normalized;
    }

    private String normalizeOptionalText(String value) {
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}