package it.zuperman.support_trainer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
@AutoConfigureTestDatabase
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
                .andExpect(status().isCreated());

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", verificationToken.getToken()))
                .andExpect(status().isOk());

        String loginRequestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
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
                .andExpect(status().isCreated());

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", verificationToken.getToken()))
                .andExpect(status().isOk());

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
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_ERROR"));
    }
}
