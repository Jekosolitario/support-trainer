package it.zuperman.support_trainer.security.session;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.zuperman.support_trainer.auth.dto.request.PasswordRecoveryConfirmRequest;
import it.zuperman.support_trainer.auth.service.PasswordRecoveryService;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.adapter.InMemoryPasswordRecoverySender;
import it.zuperman.support_trainer.email.support.PasswordRecoveryInbox;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.password.BcryptLengthAwarePasswordEncoder;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_recovery_login_case_c;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PasswordRecoveryLoginSnapshotRaceIntegrationTest.HoldMatchesUntilResetCommit.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordRecoveryLoginSnapshotRaceIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPass123!";

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

    @Test
    @DisplayName("Reset committato tra snapshot login e bcrypt deve lasciare principal con version vecchia e 401")
    void resetCommitBetweenSnapshotReadAndLoginCompletionMustRevokeCreatedSession() throws Exception {
        doNothing().when(userSessionRevocationService).revokeAllSessions(any());
        sender.clearForTesting();

        String email = "race.case.c.snapshot@example.com";
        ProfessionalProfile professional = new ProfessionalProfile(
                "CaseC",
                "Snapshot",
                email,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional = professionalProfileRepository.saveAndFlush(professional);
        Long userId = professional.getId();
        String principalName = userId.toString();

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/password-recovery/request")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        String rawToken = URI.create(PasswordRecoveryInbox.awaitSize(sender, 1).get(0).recoveryUrl())
                .getFragment()
                .substring("token=".length());

        AtomicReference<CsrfSession> racedSession = new AtomicReference<>();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> loginDuringMatches = executor.submit(() -> {
                try {
                    racedSession.set(SessionAuthTestSupport.login(mockMvc, email, PASSWORD));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            if (!HoldMatchesUntilResetCommit.MATCHES_STARTED.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("login did not reach password matches with the loaded snapshot");
            }
            assertThat(HoldMatchesUntilResetCommit.ALLOW_MATCHES.getCount()).isEqualTo(1);
            assertThat(userRepository.findByEmail(email).orElseThrow().currentSessionVersion()).isZero();

            passwordRecoveryService.confirmRecovery(new PasswordRecoveryConfirmRequest(rawToken, NEW_PASSWORD));
            assertThat(userRepository.findByEmail(email).orElseThrow().currentSessionVersion()).isEqualTo(1L);

            HoldMatchesUntilResetCommit.ALLOW_MATCHES.countDown();
            loginDuringMatches.get(15, TimeUnit.SECONDS);
        }

        CsrfSession createdSession = racedSession.get();
        assertThat(createdSession).isNotNull();
        assertThat(sessionRepository.findByPrincipalName(principalName)).isNotEmpty();

        Session springSession = sessionRepository.findByPrincipalName(principalName).values().iterator().next();
        Object storedContext = springSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY
        );
        assertThat(storedContext).isInstanceOf(SecurityContext.class);
        assertThat(((SecurityContext) storedContext).getAuthentication().getPrincipal())
                .isInstanceOf(AuthenticatedUserPrincipal.class);
        AuthenticatedUserPrincipal principal = (AuthenticatedUserPrincipal) ((SecurityContext) storedContext)
                .getAuthentication()
                .getPrincipal();
        assertThat(principal.getSessionVersion()).isZero();
        assertThat(principal.getUserId()).isEqualTo(userId);

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(createdSession)))
                .andExpect(status().isUnauthorized());
        assertThat(userRepository.findByEmail(email).orElseThrow().currentSessionVersion()).isEqualTo(1L);
    }

    @TestConfiguration
    static class HoldMatchesUntilResetCommit {

        static final CountDownLatch MATCHES_STARTED = new CountDownLatch(1);
        static final CountDownLatch ALLOW_MATCHES = new CountDownLatch(1);

        @Bean
        @Primary
        PasswordEncoder latchingPasswordEncoder() {
            PasswordEncoder delegate = new BcryptLengthAwarePasswordEncoder(new BCryptPasswordEncoder());
            return new PasswordEncoder() {
                @Override
                public String encode(CharSequence rawPassword) {
                    return delegate.encode(rawPassword);
                }

                @Override
                public boolean matches(CharSequence rawPassword, String encodedPassword) {
                    MATCHES_STARTED.countDown();
                    try {
                        if (!ALLOW_MATCHES.await(15, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("password matches was not released after reset commit");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("password matches wait interrupted", interrupted);
                    }
                    return delegate.matches(rawPassword, encodedPassword);
                }
            };
        }
    }
}
