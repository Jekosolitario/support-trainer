package it.zuperman.support_trainer.client.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.client.dto.response.ClientDetailResponse;
import it.zuperman.support_trainer.client.dto.response.ClientSummaryResponse;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.security.session.AuthenticatedUserLoader;

@Service
public class ClientService {

    private final AuthenticatedUserLoader authenticatedUserLoader;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;
    private final UserReadinessValidator userReadinessValidator;

    public ClientService(
            AuthenticatedUserLoader authenticatedUserLoader,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            UserReadinessValidator userReadinessValidator
    ) {
        this.authenticatedUserLoader = authenticatedUserLoader;
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
        User user = authenticatedUserLoader.requireAuthenticatedUser();
        userReadinessValidator.validateOperationalUser(user);
        return user;
    }
}
