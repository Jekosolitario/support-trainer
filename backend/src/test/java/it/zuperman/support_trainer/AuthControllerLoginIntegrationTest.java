package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class AuthControllerLoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    @DisplayName("Login con account esistente e password ASCII oltre 72 byte deve restituire credenziali non valide")
    void shouldRejectOversizedAsciiPasswordForExistingAccountWithGenericAuthenticationError() throws Exception {
        String email = "existing.password.over.limit@example.com";
        registerAndVerifyProfessional(email, "Password123!");

        String oversizedPassword = "A1!" + "a".repeat(70);
        assertThat(oversizedPassword).hasSize(73);
        assertThat(oversizedPassword.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        assertGenericInvalidCredentials(email, oversizedPassword);
    }

    @Test
    @DisplayName("Login con account inesistente e password Unicode oltre 72 byte deve avere lo stesso errore generico")
    void shouldRejectOversizedUnicodePasswordForMissingAccountWithGenericAuthenticationError() throws Exception {
        String oversizedPassword = "A1!" + "€".repeat(24);
        assertThat(oversizedPassword).hasSize(27);
        assertThat(oversizedPassword.getBytes(StandardCharsets.UTF_8)).hasSize(75);

        assertGenericInvalidCredentials("missing.password.over.limit@example.com", oversizedPassword);
    }

    @Test
    @DisplayName("Professionista verificato deve effettuare il login con sessione e cookie STSESSION")
    void shouldLoginVerifiedProfessionalWithSessionCookie() throws Exception {
        String email = "anna.neri@example.com";
        String password = "Password123!";
        registerAndVerifyProfessional(email, password);

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SessionAuthTestSupport.loginBody(email, password)))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist())
                .andExpect(cookie().exists("STSESSION"));

        CsrfSession session = SessionAuthTestSupport.login(mockMvc, email, password);
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Professionista verificato con profilo inactive deve comunque effettuare il login")
    void shouldLoginVerifiedProfessionalWithInactiveProfile() throws Exception {
        String email = "inactive.profile.login@example.com";
        String password = "Password123!";
        registerAndVerifyProfessional(email, password);

        ProfessionalProfile professional = (ProfessionalProfile) userRepository.findByEmail(email).orElseThrow();
        professional.setActive(false);
        userRepository.saveAndFlush(professional);

        CsrfSession session = SessionAuthTestSupport.login(mockMvc, email, password);
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Professionista verificato non deve effettuare il login con password errata")
    void shouldRejectLoginWithIncorrectPassword() throws Exception {
        String email = "paolo.bianchi@example.com";
        String password = "Password123!";
        registerAndVerifyProfessional(email, password);

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SessionAuthTestSupport.loginBody(email, "WrongPassword123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @DisplayName("Professionista non verificato non deve effettuare il login")
    void shouldRejectLoginBeforeEmailVerification() throws Exception {
        String email = "giulia.romano@example.com";
        String password = "Password123!";
        registerProfessional(email, password);

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SessionAuthTestSupport.loginBody(email, password)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    private void registerAndVerifyProfessional(String email, String password) throws Exception {
        registerProfessional(email, password);

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        confirmEmail(verificationToken.getToken());
    }

    private void registerProfessional(String email, String password) throws Exception {
        String registrationRequestBody = """
                {
                  "firstName": "Anna",
                  "lastName": "Neri",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email, password);

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestBody))
                .andExpect(status().isAccepted());
    }

    private void confirmEmail(String token) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk());
    }

    private void assertGenericInvalidCredentials(String email, String password) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SessionAuthTestSupport.loginBody(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Credenziali non valide"));
    }
}
