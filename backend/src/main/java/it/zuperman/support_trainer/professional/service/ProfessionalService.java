package it.zuperman.support_trainer.professional.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.dto.response.ProfessionalDetailResponse;
import it.zuperman.support_trainer.professional.dto.response.ProfessionalSummaryResponse;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.security.session.AuthenticatedUserLoader;

@Service
public class ProfessionalService {

    private final AuthenticatedUserLoader authenticatedUserLoader;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;
    private final UserReadinessValidator userReadinessValidator;

    public ProfessionalService(
            AuthenticatedUserLoader authenticatedUserLoader,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            UserReadinessValidator userReadinessValidator
    ) {
        this.authenticatedUserLoader = authenticatedUserLoader;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
        this.userReadinessValidator = userReadinessValidator;
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
        User user = authenticatedUserLoader.requireAuthenticatedUser();
        userReadinessValidator.validateOperationalUser(user);
        return user;
    }
}
