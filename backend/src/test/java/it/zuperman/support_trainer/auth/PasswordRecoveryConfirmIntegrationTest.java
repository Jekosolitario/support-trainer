package it.zuperman.support_trainer.auth;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import it.zuperman.support_trainer.auth.passwordrecovery.PasswordResetTokenHasher;
import it.zuperman.support_trainer.auth.repository.PasswordResetTokenRepository;
import it.zuperman.support_trainer.auth.token.PasswordResetToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.adapter.InMemoryPasswordRecoverySender;
import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration;
import it.zuperman.support_trainer.email.support.PasswordRecoveryInbox;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.MutableTestClock;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

import static it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(EmailTestClockConfiguration.class)
class PasswordRecoveryConfirmIntegrationTest {

    private static final String REQUEST_ENDPOINT = "/api/v1/auth/password-recovery/request";
    private static final String CONFIRM_ENDPOINT = "/api/v1/auth/password-recovery/confirm";
    private static final String PASSWORD = "Password123!";
    private static final String NEW_PASSWORD = "NewPass123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPasswordRecoverySender sender;
    @Autowired
    private MutableTestClock clock;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        clock.setInstant(INITIAL_INSTANT);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Confirm happy path aggiorna bcrypt, consuma il token e non crea sessione")
    void successfulConfirmMustUpdatePasswordWithoutAutoLogin() throws Exception {
        User user = createEligibleProfessional("confirm.happy@example.com");
        String rawToken = requestRawToken(user.getEmail());

        MvcResult result = confirm(rawToken, NEW_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getContentAsString()).isEmpty();

        User updated = userRepository.findByEmail(user.getEmail()).orElseThrow();
        PasswordResetToken consumed = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId()).get(0);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, updated.getPassword())).isFalse();
        assertThat(updated.currentSessionVersion()).isEqualTo(1L);
        assertThat(consumed.getConsumedAt()).isEqualTo(INITIAL_INSTANT);
        assertThat(consumed.getTokenHash()).isEqualTo(PasswordResetTokenHasher.sha256Hex(rawToken));

        mockMvc.perform(get("/api/v1/me/account"))
                .andExpect(status().isUnauthorized());

        loginMustFail(user.getEmail(), PASSWORD);
        CsrfSession session = SessionAuthTestSupport.login(mockMvc, user.getEmail(), NEW_PASSWORD);
        String meBody = mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(session)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(meBody)
                .contains("\"role\":\"PROFESSIONAL\"")
                .doesNotContain("CLIENT");
    }

    @Test
    void unknownExpiredConsumedAndInvalidatedTokensMustSharePublicError() throws Exception {
        User user = createEligibleProfessional("confirm.invalid@example.com");
        String rawToken = requestRawToken(user.getEmail());

        confirmMustReject("unknown-token-value", NEW_PASSWORD);

        clock.setInstant(INITIAL_INSTANT.plus(Duration.ofMinutes(30)));
        confirmMustReject(rawToken, NEW_PASSWORD);

        clock.setInstant(INITIAL_INSTANT);
        sender.clearForTesting();
        tokenRepository.deleteAll();
        String reusable = requestRawToken(user.getEmail());
        confirm(reusable, NEW_PASSWORD);
        confirmMustReject(reusable, "Another1!");

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        sender.clearForTesting();
        User other = createEligibleProfessional("confirm.invalidated@example.com");
        String firstRaw = requestRawToken(other.getEmail());
        clock.setInstant(INITIAL_INSTANT.plusSeconds(120));
        requestRawToken(other.getEmail());
        confirmMustReject(firstRaw, NEW_PASSWORD);
    }

    @Test
    void userThatBecameIneligibleMustReceiveTheSameTokenError() throws Exception {
        User user = createEligibleProfessional("confirm.ineligible@example.com");
        String rawToken = requestRawToken(user.getEmail());
        user.setEmailVerified(false);
        user.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        userRepository.saveAndFlush(user);

        confirmMustReject(rawToken, NEW_PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findByEmail(user.getEmail()).orElseThrow().getPassword()))
                .isTrue();
    }

    @Test
    void invalidPasswordMustReturnValidationErrorWithoutConsumingToken() throws Exception {
        User user = createEligibleProfessional("confirm.password@example.com");
        String rawToken = requestRawToken(user.getEmail());

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(rawToken, "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400));

        PasswordResetToken stored = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId()).get(0);
        assertThat(stored.getConsumedAt()).isNull();
        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findByEmail(user.getEmail()).orElseThrow().getPassword()))
                .isTrue();
    }

    @Test
    void successfulConfirmMustInvalidateOtherOpenTokens() throws Exception {
        User user = createEligibleProfessional("confirm.others@example.com");
        String rawToken = requestRawToken(user.getEmail());
        PasswordResetToken extra = new PasswordResetToken(
                user,
                PasswordResetTokenHasher.sha256Hex("another-open-token"),
                INITIAL_INSTANT.plus(Duration.ofMinutes(30))
        );
        tokenRepository.saveAndFlush(extra);

        confirm(rawToken, NEW_PASSWORD);

        List<PasswordResetToken> tokens = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());
        assertThat(tokens).hasSize(2);
        assertThat(tokens).allSatisfy(token -> assertThat(token.getConsumedAt()).isEqualTo(INITIAL_INSTANT));
    }

    @Test
    void confirmWithoutCsrfMustFailAndWithCsrfMustReachService() throws Exception {
        User user = createEligibleProfessional("confirm.csrf@example.com");
        String rawToken = requestRawToken(user.getEmail());
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);

        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .with(SessionAuthTestSupport.withSession(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(rawToken, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(rawToken, NEW_PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/me/account")
                        .with(SessionAuthTestSupport.withSession(csrf)))
                .andExpect(status().isUnauthorized());
    }

    private void confirmMustReject(String token, String newPassword) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        String body = mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(token, newPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID_OR_EXPIRED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(CONFIRM_ENDPOINT))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body)
                .doesNotContain("USER_NOT_FOUND")
                .doesNotContain(token)
                .doesNotContain(newPassword);
    }

    private MvcResult confirm(String token, String newPassword) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        return mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(token, newPassword)))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    private String requestRawToken(String email) throws Exception {
        int messagesBeforeRequest = sender.messages().size();
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post(REQUEST_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        PasswordRecoveryMessage message = PasswordRecoveryInbox.awaitSize(sender, messagesBeforeRequest + 1)
                .get(messagesBeforeRequest);
        String fragment = URI.create(message.recoveryUrl()).getFragment();
        return fragment.substring("token=".length());
    }

    private void loginMustFail(String email, String password) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SessionAuthTestSupport.loginBody(email, password)))
                .andExpect(status().isUnauthorized());
    }

    private static String confirmBody(String token, String newPassword) {
        return """
                {
                  "token": "%s",
                  "newPassword": "%s"
                }
                """.formatted(token, newPassword);
    }

    private ProfessionalProfile createEligibleProfessional(String email) {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Anna",
                "Neri",
                email,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        return professionalProfileRepository.saveAndFlush(professional);
    }

    private void cleanDatabase() {
        sender.clearForTesting();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
