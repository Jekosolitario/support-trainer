package it.zuperman.support_trainer;

import java.nio.charset.StandardCharsets;

import com.jayway.jsonpath.JsonPath;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.repository.UserRepository;
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
    @DisplayName("Professionista verificato deve effettuare il login e ricevere i token")
    void shouldLoginVerifiedProfessionalAndReturnTokens() throws Exception {
        String email = "anna.neri@example.com";
        String password = "Password123!";
        String registrationRequestBody = """
                {
                  "firstName": "Anna",
                  "lastName": "Neri",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestBody))
                .andExpect(status().isAccepted());

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        confirmEmail(verificationToken.getToken());

        String loginRequestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.accessToken");
        String refreshToken = JsonPath.read(responseBody, "$.refreshToken");

        mockMvc.perform(get("/api/v1/me/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Professionista verificato non deve effettuare il login con password errata")
    void shouldRejectLoginWithIncorrectPassword() throws Exception {
        String email = "paolo.bianchi@example.com";
        String password = "Password123!";
        String registrationRequestBody = """
                {
                  "firstName": "Paolo",
                  "lastName": "Bianchi",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestBody))
                .andExpect(status().isAccepted());

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        confirmEmail(verificationToken.getToken());

        String loginRequestBody = """
                {
                  "email": "%s",
                  "password": "WrongPassword123!"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"));
    }

    @Test
    @DisplayName("Professionista non verificato non deve effettuare il login")
    void shouldRejectLoginBeforeEmailVerification() throws Exception {
        String email = "giulia.romano@example.com";
        String password = "Password123!";
        String registrationRequestBody = """
                {
                  "firstName": "Giulia",
                  "lastName": "Romano",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestBody))
                .andExpect(status().isAccepted());

        String loginRequestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    }

    private void registerAndVerifyProfessional(String email, String password) throws Exception {
        String registrationRequestBody = """
                {
                  "firstName": "Password",
                  "lastName": "Limit",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestBody))
                .andExpect(status().isAccepted());

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        confirmEmail(verificationToken.getToken());
    }

    private void confirmEmail(String token) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk());
    }

    private void assertGenericInvalidCredentials(String email, String password) throws Exception {
        String loginRequestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Credenziali non valide"));
    }
}
