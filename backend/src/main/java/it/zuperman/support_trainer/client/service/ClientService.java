package it.zuperman.support_trainer.client.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.client.dto.response.ClientDetailResponse;
import it.zuperman.support_trainer.client.dto.response.ClientSummaryResponse;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

@Service
public class ClientService {

    private final UserRepository userRepository;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;
    private final UserReadinessValidator userReadinessValidator;

    public ClientService(
            UserRepository userRepository,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            UserReadinessValidator userReadinessValidator
    ) {
        this.userRepository = userRepository;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
        this.userReadinessValidator = userReadinessValidator;
    }

    @Transactional(readOnly = true)
    public List<ClientSummaryResponse> getMyClients() {
        ProfessionalProfile authenticatedProfessional = getAuthenticatedProfessional();

        List<ProfessionalClientLink> links
                = professionalClientLinkRepository.findAllByProfessional_IdAndActiveTrue(authenticatedProfessional.getId());

        return links.stream()
                .map(ProfessionalClientLink::getClient)
                .filter(this::isReadableClient)
                .map(ClientSummaryResponse::fromClient)
                .toList();
    }

    private boolean isReadableClient(ClientProfile client) {
        return Boolean.TRUE.equals(client.getActive())
                && client.getAccountStatus() == AccountStatus.ACTIVE
                && Boolean.TRUE.equals(client.getEmailVerified());
    }

    @Transactional(readOnly = true)
    public ClientDetailResponse getClientDetail(Long clientId) {
        ProfessionalProfile authenticatedProfessional = getAuthenticatedProfessional();
        ClientProfile client = getAccessibleClient(authenticatedProfessional.getId(), clientId);

        return ClientDetailResponse.fromClient(client);
    }

    private ClientProfile getAccessibleClient(Long professionalId, Long clientId) {
        return professionalClientLinkRepository.findAccessibleClient(
                professionalId,
                clientId,
                AccountStatus.ACTIVE
        ).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND,
                "CLIENT_NOT_FOUND",
                "Cliente non trovato"
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
