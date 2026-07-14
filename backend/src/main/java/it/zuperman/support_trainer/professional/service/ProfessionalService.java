package it.zuperman.support_trainer.professional.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.dto.response.ProfessionalDetailResponse;
import it.zuperman.support_trainer.professional.dto.response.ProfessionalSummaryResponse;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

@Service
public class ProfessionalService {

    private final UserRepository userRepository;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;

    public ProfessionalService(
            UserRepository userRepository,
            ProfessionalClientLinkRepository professionalClientLinkRepository
    ) {
        this.userRepository = userRepository;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfessionalSummaryResponse> getMyProfessionals() {
        ClientProfile authenticatedClient = getAuthenticatedClient();

        List<ProfessionalClientLink> links
                = professionalClientLinkRepository.findAllByClient_IdAndActiveTrue(authenticatedClient.getId());

        return links.stream()
                .map(ProfessionalClientLink::getProfessional)
                .filter(this::isReadableProfessional)
                .map(ProfessionalSummaryResponse::fromProfessional)
                .toList();
    }

    private boolean isReadableProfessional(ProfessionalProfile professional) {
        return Boolean.TRUE.equals(professional.getActive())
                && professional.getAccountStatus() == AccountStatus.ACTIVE
                && Boolean.TRUE.equals(professional.getEmailVerified());
    }

    @Transactional(readOnly = true)
    public ProfessionalDetailResponse getProfessionalDetail(Long professionalId) {
        ClientProfile authenticatedClient = getAuthenticatedClient();
        ProfessionalProfile professional = getAccessibleProfessional(authenticatedClient.getId(), professionalId);

        return ProfessionalDetailResponse.fromProfessional(professional);
    }

    private ProfessionalProfile getAccessibleProfessional(Long clientId, Long professionalId) {
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
}
