package it.zuperman.support_trainer.security.session;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_recovery_cleanup_failure;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordRecoveryPhysicalCleanupFailureIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPass123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private InMemoryPasswordRecoverySender sender;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @MockitoBean
    private UserSessionRevocationService userSessionRevocationService;

    @BeforeEach
    void setUp() {
        sender.clearForTesting();
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
        doThrow(new IllegalStateException("physical cleanup unavailable"))
                .when(userSessionRevocationService)
                .revokeAllSessions(any());
    }

    @Test
    @DisplayName("Cleanup fisico fallito lascia la riga Spring Session ma la request protetta resta 401")
    void leftoverSessionRowMustStillBeUnauthorizedAfterSessionVersionCommit() throws Exception {
        String email = "cleanup.failure.session@example.com";
        ProfessionalProfile professional = new ProfessionalProfile(
                "Cleanup",
                "Failure",
                email,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional = professionalProfileRepository.saveAndFlush(professional);
        Long userId = professional.getId();
        String principalName = userId.toString();

        CsrfSession existingSession = SessionAuthTestSupport.login(mockMvc, email, PASSWORD);
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(existingSession)))
                .andExpect(status().isOk());
        assertThat(sessionRepository.findByPrincipalName(principalName)).isNotEmpty();

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/password-recovery/request")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        String rawToken = URI.create(PasswordRecoveryInbox.awaitSize(sender, 1).get(0).recoveryUrl())
                .getFragment()
                .substring("token=".length());

        CsrfSession confirmCsrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/password-recovery/confirm")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(confirmCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(rawToken, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        verify(userSessionRevocationService, timeout(5_000)).revokeAllSessions(userId);
        assertThat(userRepository.findByEmail(email).orElseThrow().currentSessionVersion()).isEqualTo(1L);
        assertThat(sessionRepository.findByPrincipalName(principalName)).isNotEmpty();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                        String.class,
                        principalName
                ))
                .contains(principalName);

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(existingSession)))
                .andExpect(status().isUnauthorized());
    }
}
