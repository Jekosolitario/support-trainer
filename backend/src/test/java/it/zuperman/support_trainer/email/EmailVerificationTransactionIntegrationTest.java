package it.zuperman.support_trainer.email;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import it.zuperman.support_trainer.auth.dto.request.RegisterClientRequest;
import it.zuperman.support_trainer.auth.dto.request.RegisterProfessionalRequest;
import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.service.AuthService;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.adapter.InMemoryEmailVerificationSender;
import it.zuperman.support_trainer.email.event.EmailVerificationRequestedEvent;
import it.zuperman.support_trainer.email.model.EmailVerificationReason;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.MutableTestClock;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

import static it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(EmailTestClockConfiguration.class)
class EmailVerificationTransactionIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private AuthService authService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private InMemoryEmailVerificationSender sender;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfessionalProfileRepository professionalRepository;
    @Autowired
    private ClientProfileRepository clientRepository;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;
    @Autowired
    private InviteCodeRepository inviteRepository;
    @Autowired
    private ProfessionalClientLinkRepository linkRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private MutableTestClock clock;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        cleanDatabase();
        clock.setInstant(INITIAL_INSTANT);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void committedEventShouldProduceExactlyOneMessage() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event("commit@example.com")));

        assertThat(sender.messages()).hasSize(1);
        assertThat(sender.messages().get(0).recipient()).isEqualTo("commit@example.com");
    }

    @Test
    void eventPublishedWithoutTransactionShouldBeDiscarded() {
        eventPublisher.publishEvent(event("no-transaction@example.com"));

        assertThat(sender.messages()).isEmpty();
    }

    @Test
    void professionalRegistrationRollbackShouldDiscardDataAndMessage() {
        RegisterProfessionalRequest request = new RegisterProfessionalRequest(
                "Rollback", "Professional", "rollback.professional@example.com", PASSWORD,
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        transactionTemplate.executeWithoutResult(status -> {
            authService.registerProfessional(request);
            status.setRollbackOnly();
        });

        assertThat(userRepository.findByEmail(request.getEmail())).isEmpty();
        assertThat(tokenRepository.count()).isZero();
        assertThat(sender.messages()).isEmpty();
    }

    @Test
    void clientRegistrationRollbackShouldRestoreInviteAndDiscardLinkTokenAndMessage() {
        ProfessionalProfile professional = verifiedProfessional("owner.rollback@example.com");
        InviteCode invite = transactionTemplate.execute(status -> {
            ProfessionalProfile savedProfessional = professionalRepository.saveAndFlush(professional);
            return inviteRepository.saveAndFlush(new InviteCode(
                    "INV-ROLLBACK",
                    savedProfessional,
                    INITIAL_INSTANT.plus(Duration.ofDays(7))
            ));
        });
        RegisterClientRequest request = new RegisterClientRequest(
                "Rollback", "Client", "rollback.client@example.com", PASSWORD,
                invite.getCode(), LocalDate.of(1996, 4, 15), new BigDecimal("178.00"),
                "Obiettivo", Gender.MALE, null, null, null
        );

        transactionTemplate.executeWithoutResult(status -> {
            authService.registerClient(request);
            status.setRollbackOnly();
        });

        InviteCode persistedInvite = inviteRepository.findByCode(invite.getCode()).orElseThrow();
        assertThat(clientRepository.findByEmail(request.getEmail())).isEmpty();
        assertThat(persistedInvite.getUsed()).isFalse();
        assertThat(persistedInvite.getUsedAt()).isNull();
        assertThat(linkRepository.count()).isZero();
        assertThat(tokenRepository.count()).isZero();
        assertThat(sender.messages()).isEmpty();
    }

    @Test
    void resendRollbackShouldDiscardNewTokenAndMessage() {
        ProfessionalProfile pending = new ProfessionalProfile(
                "Rollback", "Resend", "rollback.resend@example.com", "encoded",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professionalRepository.saveAndFlush(pending);

        transactionTemplate.executeWithoutResult(status -> {
            authService.resendEmailVerification(pending.getEmail());
            status.setRollbackOnly();
        });

        assertThat(tokenRepository.count()).isZero();
        assertThat(sender.messages()).isEmpty();
    }

    private EmailVerificationRequestedEvent event(String recipient) {
        return new EmailVerificationRequestedEvent(
                recipient,
                "event-token",
                INITIAL_INSTANT.plus(Duration.ofHours(24)),
                EmailVerificationReason.REGISTRATION,
                UUID.randomUUID()
        );
    }

    private ProfessionalProfile verifiedProfessional(String email) {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Verified", "Professional", email, "encoded",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setEmailVerified(true);
        professional.setAccountStatus(AccountStatus.ACTIVE);
        return professional;
    }

    private void cleanDatabase() {
        sender.clearForTesting();
        tokenRepository.deleteAll();
        linkRepository.deleteAll();
        inviteRepository.deleteAll();
        userRepository.deleteAll();
    }
}
