package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class AuthControllerEmailVerificationIntegrationTest {

    private static final String CONFIRM_ENDPOINT = "/api/v1/auth/email-verification/confirm";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private ApplicationTimeProvider timeProvider;

    @Test
    @DisplayName("POST deve verificare correttamente l'email e non restituire JWT")
    void shouldConfirmProfessionalEmailSuccessfully() throws Exception {
        String email = "mario.rossi@example.com";
        registerProfessional(email);

        User savedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(savedUser.getEmailVerified()).isFalse();
        assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        EmailVerificationToken verificationToken = findTokenFor(savedUser);
        assertThat(verificationToken.getUsed()).isFalse();
        assertThat(verificationToken.getToken()).isNotBlank();

        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(verificationToken.getToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verificata correttamente"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        User verifiedUser = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken usedToken = findTokenFor(verifiedUser);
        assertThat(verifiedUser.getEmailVerified()).isTrue();
        assertThat(verifiedUser.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(usedToken.getUsed()).isTrue();
        assertThat(usedToken.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Il secondo POST sullo stesso token deve essere idempotente")
    void shouldReturnSuccessWithoutChangingUsedAtOnSecondConfirmation() throws Exception {
        String email = "idempotent.verification@example.com";
        registerProfessional(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token = findTokenFor(user);

        confirm(token.getToken());
        Instant firstUsedAt = findTokenFor(user).getUsedAt();
        long tokenCount = emailVerificationTokenRepository.count();

        confirm(token.getToken());

        EmailVerificationToken confirmedAgain = findTokenFor(user);
        assertThat(confirmedAgain.getUsedAt()).isEqualTo(firstUsedAt);
        assertThat(confirmedAgain.getUsed()).isTrue();
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(emailVerificationTokenRepository.count()).isEqualTo(tokenCount);
    }

    @Test
    @DisplayName("Token inesistente deve restituire not found")
    void shouldReturnNotFoundForMissingEmailVerificationToken() throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("inesistente")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_NOT_FOUND"));
    }

    @Test
    @DisplayName("Body assente deve restituire malformed request")
    void shouldRejectMissingBody() throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Token mancante o null deve restituire validation error")
    void shouldRejectMissingOrNullToken() throws Exception {
        assertInvalidTokenBody("{}");
        assertInvalidTokenBody("{\"token\":null}");
    }

    @Test
    @DisplayName("Token vuoto o composto da spazi deve restituire validation error")
    void shouldRejectBlankToken() throws Exception {
        assertInvalidTokenBody("{\"token\":\"\"}");
        assertInvalidTokenBody("{\"token\":\"   \"}");
    }

    @Test
    @DisplayName("Token oltre 500 caratteri deve restituire validation error")
    void shouldRejectTokenLongerThanDatabaseColumn() throws Exception {
        assertInvalidTokenBody(tokenBody("a".repeat(501)));
    }

    @Test
    @DisplayName("JSON malformato deve restituire malformed request")
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Content-Type non supportato deve restituire 415")
    void shouldRejectUnsupportedContentType() throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<token>value</token>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("Token scaduto deve restituire gone senza attivare l'utente")
    void shouldRejectExpiredEmailVerificationTokenWithGone() throws Exception {
        String email = "expired.token@example.com";
        registerProfessional(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token = findTokenFor(user);
        token.setExpiresAt(timeProvider.nowInstant());
        emailVerificationTokenRepository.saveAndFlush(token);

        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token.getToken())))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_EXPIRED"));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailVerified()).isFalse();
        assertThat(token.getUsed()).isFalse();
    }

    @Test
    @DisplayName("Token usato con stato incoerente deve restituire already used")
    void shouldRejectUsedTokenWithInconsistentUserState() throws Exception {
        String email = "inconsistent.used.token@example.com";
        registerProfessional(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token = findTokenFor(user);
        token.markAsUsed(timeProvider.nowInstant());
        emailVerificationTokenRepository.saveAndFlush(token);

        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token.getToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_ALREADY_USED"));
    }

    @Test
    @DisplayName("Profilo disattivato non deve essere riattivato dalla conferma")
    void shouldNotReactivateInactiveProfile() throws Exception {
        String email = "inactive.profile@example.com";
        registerProfessional(email);
        ProfessionalProfile professional = (ProfessionalProfile) userRepository.findByEmail(email).orElseThrow();
        professional.setActive(false);
        userRepository.saveAndFlush(professional);
        EmailVerificationToken token = findTokenFor(professional);

        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token.getToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_ACTIVE"));

        assertThat(professional.getActive()).isFalse();
        assertThat(professional.getEmailVerified()).isFalse();
        assertThat(professional.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(token.getUsed()).isFalse();
    }

    @Test
    @DisplayName("Il vecchio GET deve essere rimosso e non consumare il token")
    void shouldRemoveLegacyGetWithoutMutatingVerificationState() throws Exception {
        String email = "legacy.get.removed@example.com";
        registerProfessional(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token = findTokenFor(user);

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", token.getToken()))
                .andExpect(status().isNotFound());

        assertThat(user.getEmailVerified()).isFalse();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(token.getUsed()).isFalse();

        confirm(token.getToken());
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private void registerProfessional(String email) throws Exception {
        String requestBody = """
                {
                  "firstName": "Mario",
                  "lastName": "Rossi",
                  "email": "%s",
                  "password": "Password123!",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted());
    }

    private EmailVerificationToken findTokenFor(User user) {
        return emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
    }

    private void confirm(String token) throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verificata correttamente"));
    }

    private void assertInvalidTokenBody(String body) throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'token')]").isNotEmpty());
    }

    private String tokenBody(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }
}
