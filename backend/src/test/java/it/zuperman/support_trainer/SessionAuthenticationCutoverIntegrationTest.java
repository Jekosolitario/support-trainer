package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.session.SessionAuthenticationStateFilter;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class SessionAuthenticationCutoverIntegrationTest {

    private static final String EMAIL = "session.cutover@example.com";
    private static final String CLIENT_EMAIL = "session.cutover.client@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionAuthenticationStateFilter sessionAuthenticationStateFilter;

    @Autowired
    private FilterRegistrationBean<SessionAuthenticationStateFilter> sessionAuthenticationStateFilterRegistration;

    @Test
    @DisplayName("CSRF: T0 pre-login invalido dopo login; T1 logout 204 sulla stessa sessione")
    void shouldRejectPreLoginCsrfTokenAfterLoginAndAcceptRotatedToken() throws Exception {
        createVerifiedProfessional(true);

        CsrfSession anonymousCsrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        String tokenT0 = anonymousCsrf.token();
        assertThat(tokenT0).isNotBlank();
        assertThat(anonymousCsrf.headerName()).isNotBlank();

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(anonymousCsrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SessionAuthTestSupport.loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("STSESSION"))
                .andReturn();

        CsrfSession authenticatedSession = new CsrfSession(
                SessionAuthTestSupport.mergeCookies(
                        anonymousCsrf.cookies(),
                        loginResult.getResponse().getCookies()
                ),
                tokenT0,
                anonymousCsrf.headerName()
        );

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(authenticatedSession)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ignored-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));

        CsrfSession postLoginCsrf = SessionAuthTestSupport.fetchCsrf(mockMvc, authenticatedSession);
        String tokenT1 = postLoginCsrf.token();
        assertThat(tokenT1).isNotBlank();
        assertThat(tokenT1).isNotEqualTo(tokenT0);

        CsrfSession stalePreLoginToken = new CsrfSession(
                postLoginCsrf.cookies(),
                tokenT0,
                postLoginCsrf.headerName()
        );
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(stalePreLoginToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(postLoginCsrf)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(postLoginCsrf)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(postLoginCsrf)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PROFESSIONAL con profile.active=false deve fare login 204 e accedere a /me")
    void shouldLoginProfessionalWithInactiveOperationalProfile() throws Exception {
        createVerifiedProfessional(false);

        CsrfSession session = SessionAuthTestSupport.login(mockMvc, EMAIL, PASSWORD);
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/profile")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CLIENT con profile.active=false deve fare login 204 e accedere a /me")
    void shouldLoginClientWithInactiveOperationalProfile() throws Exception {
        createVerifiedClient(false);

        CsrfSession session = SessionAuthTestSupport.login(mockMvc, CLIENT_EMAIL, PASSWORD);
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/profile")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("State filter deve usare entry point quando l'account non è più eligible")
    void stateFilterMustUseAuthenticationEntryPointWhenAccountNoLongerEligible() throws Exception {
        ProfessionalProfile professional = createVerifiedProfessional(true);
        CsrfSession session = SessionAuthTestSupport.login(mockMvc, EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());

        professional.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        professional.setEmailVerified(false);
        userRepository.saveAndFlush(professional);

        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("FilterRegistrationBean del state filter deve essere disabilitato")
    void sessionAuthenticationStateFilterRegistrationMustBeDisabled() {
        assertThat(sessionAuthenticationStateFilterRegistration.getFilter())
                .isSameAs(sessionAuthenticationStateFilter);
        assertThat(sessionAuthenticationStateFilterRegistration.isEnabled()).isFalse();
    }

    private ProfessionalProfile createVerifiedProfessional(boolean active) {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Cutover",
                "Test",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(active);
        return professionalProfileRepository.saveAndFlush(professional);
    }

    private ClientProfile createVerifiedClient(boolean active) {
        ClientProfile client = new ClientProfile(
                "Cutover",
                "Client",
                CLIENT_EMAIL,
                passwordEncoder.encode(PASSWORD),
                LocalDate.of(1990, 1, 1),
                BigDecimal.valueOf(175),
                "Obiettivo test",
                Gender.MALE
        );
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        client.setActive(active);
        return clientProfileRepository.saveAndFlush(client);
    }
}
