package it.zuperman.support_trainer.security.session;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import it.zuperman.support_trainer.auth.dto.request.PasswordRecoveryConfirmRequest;
import it.zuperman.support_trainer.auth.service.PasswordRecoveryService;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.adapter.InMemoryPasswordRecoverySender;
import it.zuperman.support_trainer.email.support.PasswordRecoveryInbox;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_recovery_login_race;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PasswordRecoveryOldPasswordLoginRaceIntegrationTest.HoldResetCommitUntilOldPasswordLogin.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordRecoveryOldPasswordLoginRaceIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPass123!";

    static final CountDownLatch RESET_REACHED_PRE_COMMIT = new CountDownLatch(1);
    static final CountDownLatch LOGIN_COMPLETED = new CountDownLatch(1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PasswordRecoveryService passwordRecoveryService;
    @Autowired
    private InMemoryPasswordRecoverySender sender;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @MockitoBean
    private UserSessionRevocationService userSessionRevocationService;

    @BeforeEach
    void setUp() {
        sender.clearForTesting();
        doNothing().when(userSessionRevocationService).revokeAllSessions(any());
    }

    @Test
    @DisplayName("Sessione creata con la vecchia password prima del commit del reset deve ricevere 401")
    void sessionCreatedWithOldPasswordBeforeResetCommitMustBeUnauthorizedAfterCommit() throws Exception {
        String email = "race.old.password.login@example.com";
        ProfessionalProfile professional = new ProfessionalProfile(
                "Race",
                "Login",
                email,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional = professionalProfileRepository.saveAndFlush(professional);

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/password-recovery/request")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        String rawToken = URI.create(PasswordRecoveryInbox.awaitSize(sender, 1).get(0).recoveryUrl())
                .getFragment()
                .substring("token=".length());

        AtomicReference<CsrfSession> oldPasswordSession = new AtomicReference<>();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> loginDuringReset = executor.submit(() -> {
                try {
                    if (!RESET_REACHED_PRE_COMMIT.await(15, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("reset did not reach pre-commit");
                    }
                    CsrfSession session = SessionAuthTestSupport.login(mockMvc, email, PASSWORD);
                    mockMvc.perform(get("/api/v1/me/account")
                                    .with(SessionAuthTestSupport.withSession(session)))
                            .andExpect(status().isOk());
                    oldPasswordSession.set(session);
                    LOGIN_COMPLETED.countDown();
                } catch (Exception exception) {
                    LOGIN_COMPLETED.countDown();
                    throw new RuntimeException(exception);
                }
            });

            passwordRecoveryService.confirmRecovery(new PasswordRecoveryConfirmRequest(rawToken, NEW_PASSWORD));
            loginDuringReset.get(15, TimeUnit.SECONDS);
        }

        assertThat(oldPasswordSession.get()).isNotNull();
        assertThat(userRepository.findByEmail(email).orElseThrow().currentSessionVersion()).isEqualTo(1L);
        assertThat(sessionRepository.findByPrincipalName(professional.getId().toString())).isNotEmpty();

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(oldPasswordSession.get())))
                .andExpect(status().isUnauthorized());
    }

    static class HoldResetCommitUntilOldPasswordLogin {

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
        public void holdResetCommit(UserSessionsPhysicalCleanupRequestedEvent event) throws InterruptedException {
            RESET_REACHED_PRE_COMMIT.countDown();
            if (!LOGIN_COMPLETED.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("old-password login did not complete before reset commit");
            }
        }
    }
}
