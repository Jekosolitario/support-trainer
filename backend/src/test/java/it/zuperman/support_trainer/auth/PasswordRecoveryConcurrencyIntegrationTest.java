package it.zuperman.support_trainer.auth;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import it.zuperman.support_trainer.auth.dto.request.PasswordRecoveryConfirmRequest;
import it.zuperman.support_trainer.auth.dto.request.PasswordRecoveryRequest;
import it.zuperman.support_trainer.auth.repository.PasswordResetTokenRepository;
import it.zuperman.support_trainer.auth.service.PasswordRecoveryService;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.adapter.InMemoryPasswordRecoverySender;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration;
import it.zuperman.support_trainer.email.support.PasswordRecoveryInbox;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;

import static it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(EmailTestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordRecoveryConcurrencyIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPass123!";

    @Autowired
    private PasswordRecoveryService passwordRecoveryService;
    @Autowired
    private InMemoryPasswordRecoverySender sender;
    @Autowired
    private EmailTestClockConfiguration.MutableTestClock clock;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        clock.setInstant(INITIAL_INSTANT);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Due confirm concorrenti sullo stesso token: esattamente un successo e un fallimento token-invalid")
    void concurrentConfirmMustAllowExactlyOneSuccess() throws Exception {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Concurrent",
                "Reset",
                "confirm.concurrent@example.com",
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professionalProfileRepository.saveAndFlush(professional);

        passwordRecoveryService.requestRecovery(new PasswordRecoveryRequest(professional.getEmail()));
        String rawToken = URI.create(PasswordRecoveryInbox.awaitSize(sender, 1).get(0).recoveryUrl())
                .getFragment()
                .substring("token=".length());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startRace = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> attemptConfirm(rawToken, ready, startRace));
            Future<String> second = executor.submit(() -> attemptConfirm(rawToken, ready, startRace));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startRace.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED");
        }

        assertThat(tokenRepository.findByUser_IdAndConsumedAtIsNull(professional.getId())).isEmpty();
        assertThat(passwordEncoder.matches(
                NEW_PASSWORD,
                userRepository.findByEmail(professional.getEmail()).orElseThrow().getPassword()
        )).isTrue();
    }

    private String attemptConfirm(String rawToken, CountDownLatch ready, CountDownLatch startRace)
            throws InterruptedException {
        ready.countDown();
        if (!startRace.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("La contesa non è partita entro il timeout");
        }
        try {
            passwordRecoveryService.confirmRecovery(new PasswordRecoveryConfirmRequest(rawToken, NEW_PASSWORD));
            return "SUCCESS";
        } catch (AppException exception) {
            return exception.getErrorCode();
        }
    }

    private void cleanDatabase() {
        sender.clearForTesting();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
