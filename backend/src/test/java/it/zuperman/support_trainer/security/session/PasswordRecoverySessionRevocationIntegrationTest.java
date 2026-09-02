package it.zuperman.support_trainer.security.session;

import java.net.URI;
import java.util.UUID;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.email.adapter.InMemoryPasswordRecoverySender;
import it.zuperman.support_trainer.email.support.PasswordRecoveryInbox;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PasswordRecoverySessionRevocationIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPass123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    @Autowired
    private InMemoryPasswordRecoverySender sender;

    @BeforeEach
    void clearState() {
        sender.clearForTesting();
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
    }

    @Test
    @DisplayName("Dopo reset, le sessioni con sessionVersion precedente devono ricevere 401 sulle API protette")
    void resetMustRevokeAllSessionsForThePrincipal() throws Exception {
        String email = "session.revoke." + UUID.randomUUID() + "@example.com";
        ProfessionalProfile professional = new ProfessionalProfile(
                "Session",
                "Revoke",
                email,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional = professionalProfileRepository.saveAndFlush(professional);
        String principalName = professional.getId().toString();

        CsrfSession firstSession = SessionAuthTestSupport.login(mockMvc, email, PASSWORD);
        CsrfSession secondSession = SessionAuthTestSupport.login(mockMvc, email, PASSWORD);

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(firstSession)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(secondSession)))
                .andExpect(status().isOk());
        assertThat(sessionRepository.findByPrincipalName(principalName).size()).isGreaterThanOrEqualTo(2);

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

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(firstSession)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(secondSession)))
                .andExpect(status().isUnauthorized());
        // Physical cleanup is best-effort after commit; empty index is the happy path, not the security invariant.
        assertThat(sessionRepository.findByPrincipalName(principalName)).isEmpty();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NOT NULL AND PRINCIPAL_NAME <> ''",
                        String.class
                ))
                .doesNotContain(principalName);
    }
}
