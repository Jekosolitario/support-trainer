package it.zuperman.support_trainer.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.email.event.EmailVerificationRequestedEvent;
import it.zuperman.support_trainer.email.model.EmailVerificationReason;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.service.InviteCodeService;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

/**
 * Persists a complete registration in a dedicated transaction boundary.
 *
 * <p>When invoked from the unauthenticated registration flow, a unique-email
 * collision is rolled back before its caller returns the neutral response.</p>
 */
@Service
public class RegistrationPersistenceService {

    private static final Duration EMAIL_VERIFICATION_TOKEN_VALIDITY = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final InviteCodeService inviteCodeService;
    private final ProfessionalClientLinkRepository professionalClientLinkRepository;
    private final ApplicationTimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final UserReadinessValidator userReadinessValidator;

    public RegistrationPersistenceService(
            UserRepository userRepository,
            ProfessionalProfileRepository professionalProfileRepository,
            ClientProfileRepository clientProfileRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            InviteCodeService inviteCodeService,
            ProfessionalClientLinkRepository professionalClientLinkRepository,
            ApplicationTimeProvider timeProvider,
            ApplicationEventPublisher eventPublisher,
            UserReadinessValidator userReadinessValidator
    ) {
        this.userRepository = userRepository;
        this.professionalProfileRepository = professionalProfileRepository;
        this.clientProfileRepository = clientProfileRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.inviteCodeService = inviteCodeService;
        this.professionalClientLinkRepository = professionalClientLinkRepository;
        this.timeProvider = timeProvider;
        this.eventPublisher = eventPublisher;
        this.userReadinessValidator = userReadinessValidator;
    }

    @Transactional
    public void registerProfessional(ProfessionalProfile professional) {
        if (userRepository.existsByEmail(professional.getEmail())) {
            return;
        }

        ProfessionalProfile savedProfessional = professionalProfileRepository.saveAndFlush(professional);
        EmailVerificationToken verificationToken = createEmailVerificationToken(savedProfessional);
        publishEmailVerificationRequested(
                savedProfessional,
                verificationToken,
                EmailVerificationReason.REGISTRATION
        );
    }

    @Transactional
    public void registerClient(ClientProfile client, String rawInviteCode) {
        InviteCode inviteCode = inviteCodeService.validateInviteCodeForRegistration(rawInviteCode);
        ProfessionalProfile professional = inviteCode.getProfessional();
        userReadinessValidator.validateOperationalUser(professional);

        if (userRepository.existsByEmail(client.getEmail())) {
            return;
        }

        ClientProfile savedClient = clientProfileRepository.saveAndFlush(client);
        createProfessionalClientLink(professional, savedClient);

        inviteCode.setUsed(true);
        inviteCode.setUsedAt(timeProvider.nowInstant());

        EmailVerificationToken verificationToken = createEmailVerificationToken(savedClient);
        publishEmailVerificationRequested(savedClient, verificationToken, EmailVerificationReason.REGISTRATION);
    }

    private EmailVerificationToken createEmailVerificationToken(User user) {
        Instant issuedAt = timeProvider.nowInstant();
        EmailVerificationToken verificationToken = new EmailVerificationToken(
                user,
                UUID.randomUUID().toString(),
                issuedAt.plus(EMAIL_VERIFICATION_TOKEN_VALIDITY)
        );

        return emailVerificationTokenRepository.save(verificationToken);
    }

    private void publishEmailVerificationRequested(
            User user,
            EmailVerificationToken verificationToken,
            EmailVerificationReason reason
    ) {
        eventPublisher.publishEvent(new EmailVerificationRequestedEvent(
                user.getEmail(),
                verificationToken.getToken(),
                verificationToken.getExpiresAt(),
                reason,
                UUID.randomUUID()
        ));
    }

    private void createProfessionalClientLink(ProfessionalProfile professional, ClientProfile client) {
        if (professional.getId().equals(client.getId())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SELF_LINK_NOT_ALLOWED",
                    "Non è possibile creare un collegamento verso se stessi"
            );
        }

        boolean activeLinkAlreadyExists
                = professionalClientLinkRepository.existsByProfessional_IdAndClient_IdAndActiveTrue(
                        professional.getId(),
                        client.getId()
                );

        if (activeLinkAlreadyExists) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "PROFESSIONAL_CLIENT_LINK_ALREADY_EXISTS",
                    "Esiste già un collegamento attivo tra professionista e cliente"
            );
        }

        long activeProfessionalCount
                = professionalClientLinkRepository.countByClient_IdAndActiveTrue(client.getId());

        if (activeProfessionalCount >= 3) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "CLIENT_MAX_PROFESSIONALS_REACHED",
                    "Il cliente ha già raggiunto il numero massimo di professionisti attivi"
            );
        }

        professionalClientLinkRepository.save(new ProfessionalClientLink(professional, client));
    }
}
