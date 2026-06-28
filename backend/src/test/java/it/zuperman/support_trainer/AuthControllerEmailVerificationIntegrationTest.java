package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
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
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.repository.UserRepository;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class AuthControllerEmailVerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    @DisplayName("Deve verificare correttamente l'email del professionista")
    void shouldVerifyProfessionalEmailSuccessfully() throws Exception {
        String requestBody = """
                {
                  "firstName": "Mario",
                  "lastName": "Rossi",
                  "email": "mario.rossi@example.com",
                  "password": "Password123!",
                  "specialization": "PERSONAL_TRAINER"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        User savedUser = userRepository.findByEmail("mario.rossi@example.com").orElseThrow();
        assertThat(savedUser.getEmailVerified()).isFalse();
        assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedUser.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(verificationToken.getUsed()).isFalse();
        assertThat(verificationToken.getToken()).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", verificationToken.getToken()))
                .andExpect(status().isOk());

        User verifiedUser = userRepository.findByEmail("mario.rossi@example.com").orElseThrow();
        EmailVerificationToken usedToken = emailVerificationTokenRepository.findByTokenForUpdate(verificationToken.getToken())
                .orElseThrow();

        assertThat(verifiedUser.getEmailVerified()).isTrue();
        assertThat(verifiedUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(usedToken.getUsed()).isTrue();
        assertThat(usedToken.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve restituire not found per un token di verifica inesistente")
    void shouldReturnNotFoundForMissingEmailVerificationToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", "inesistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFICATION_TOKEN_NOT_FOUND"));
    }
}
