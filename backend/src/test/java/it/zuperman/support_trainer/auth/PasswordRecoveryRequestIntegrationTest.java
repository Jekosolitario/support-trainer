package it.zuperman.support_trainer.auth;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

import it.zuperman.support_trainer.auth.dto.response.PasswordRecoveryAcceptedResponse;
import it.zuperman.support_trainer.auth.passwordrecovery.PasswordResetTokenHasher;
import it.zuperman.support_trainer.auth.repository.PasswordResetTokenRepository;
import it.zuperman.support_trainer.auth.token.PasswordResetToken;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Gender;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(EmailTestClockConfiguration.class)
class PasswordRecoveryRequestIntegrationTest {

    private static final String REQUEST_ENDPOINT = "/api/v1/auth/password-recovery/request";
    private static final String PASSWORD = "Password123!";

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
    private ClientProfileRepository clientProfileRepository;
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
    @DisplayName("CLIENT, PERSONAL_TRAINER e NUTRITIONIST eligible ricevono la stessa risposta neutra e un'email")
    void eligibleRolesShouldReceiveNeutralResponseAndEmail() throws Exception {
        User client = createEligibleClient("client.recovery@example.com");
        User trainer = createEligibleProfessional(
                "pt.recovery@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        User nutritionist = createEligibleProfessional(
                "nut.recovery@example.com",
                ProfessionalSpecialization.NUTRITIONIST
        );

        MvcResult clientResult = requestRecovery(client.getEmail());
        MvcResult trainerResult = requestRecovery(trainer.getEmail());
        MvcResult nutritionistResult = requestRecovery(nutritionist.getEmail());

        String expectedBody = clientResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(List.of(clientResult, trainerResult, nutritionistResult)).allSatisfy(result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(202);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(expectedBody);
        });
        assertThat(expectedBody)
                .contains(PasswordRecoveryAcceptedResponse.NEUTRAL_MESSAGE)
                .doesNotContain("token", client.getEmail());
        assertThat(PasswordRecoveryInbox.awaitSize(sender, 3))
                .extracting(PasswordRecoveryMessage::recipient)
                .containsExactlyInAnyOrder(client.getEmail(), trainer.getEmail(), nutritionist.getEmail());
    }

    @Test
    @DisplayName("Email inesistente, non verificata e non ACTIVE producono 202 identico senza token né email")
    void ineligibleAndUnknownEmailsMustNotEnumerateAccounts() throws Exception {
        createEligibleProfessional("known.recovery@example.com", ProfessionalSpecialization.PERSONAL_TRAINER);
        MvcResult eligible = requestRecovery("known.recovery@example.com");
        PasswordRecoveryInbox.awaitSize(sender, 1);
        sender.clearForTesting();
        tokenRepository.deleteAll();

        ProfessionalProfile unverified = createProfessional(
                "unverified.recovery@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        unverified.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        unverified.setEmailVerified(false);
        professionalProfileRepository.saveAndFlush(unverified);

        ProfessionalProfile pendingVerified = createProfessional(
                "pending.recovery@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        pendingVerified.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        pendingVerified.setEmailVerified(true);
        professionalProfileRepository.saveAndFlush(pendingVerified);

        MvcResult missing = requestRecovery("missing.recovery@example.com");
        MvcResult unverifiedResult = requestRecovery(unverified.getEmail());
        MvcResult nonActive = requestRecovery(pendingVerified.getEmail());

        String expected = eligible.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(List.of(missing, unverifiedResult, nonActive)).allSatisfy(result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(202);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(expected);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .doesNotContain("USER_NOT_FOUND", "EMAIL_NOT_VERIFIED", "ACCOUNT_NOT_ACTIVE");
        });
        assertThat(sender.messages()).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("Profilo operationalmente inactive resta eligible perché il login non lo blocca")
    void inactiveProfileMustRemainEligible() throws Exception {
        ProfessionalProfile professional = createEligibleProfessional(
                "inactive.profile.recovery@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setActive(false);
        professionalProfileRepository.saveAndFlush(professional);

        requestRecovery(professional.getEmail());

        assertThat(PasswordRecoveryInbox.awaitSize(sender, 1)).hasSize(1);
        assertThat(tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(professional.getId())).hasSize(1);
    }

    @Test
    void malformedEmailMustReturnValidationError() throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post(REQUEST_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(REQUEST_ENDPOINT))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(sender.messages()).isEmpty();
    }

    @Test
    void requestWithoutCsrfMustFailAndWithCsrfMustReachService() throws Exception {
        createEligibleProfessional("csrf.recovery@example.com", ProfessionalSpecialization.PERSONAL_TRAINER);
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);

        mockMvc.perform(post(REQUEST_ENDPOINT)
                        .with(SessionAuthTestSupport.withSession(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"csrf.recovery@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_VALIDATION_FAILED"));

        mockMvc.perform(post(REQUEST_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"csrf.recovery@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(PasswordRecoveryAcceptedResponse.NEUTRAL_MESSAGE))
                .andExpect(jsonPath("$.token").doesNotExist());
        assertThat(PasswordRecoveryInbox.awaitSize(sender, 1)).hasSize(1);
    }

    @Test
    void emailNormalizationMustMatchAuthService() throws Exception {
        createEligibleProfessional("normalized.recovery@example.com", ProfessionalSpecialization.PERSONAL_TRAINER);

        MvcResult result = requestRecovery("  Normalized.Recovery@EXAMPLE.com  ");

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        PasswordRecoveryMessage message = onlyMessage();
        assertThat(message.recipient()).isEqualTo("normalized.recovery@example.com");
    }

    @Test
    void issuedTokenMustPersistOnlySha256HashWithTtlAndFragmentUrl() throws Exception {
        User user = createEligibleProfessional(
                "hash.recovery@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        MvcResult result = requestRecovery(user.getEmail());
        PasswordRecoveryMessage message = onlyMessage();
        String rawToken = rawTokenFrom(message);
        PasswordResetToken stored = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId()).get(0);

        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(rawToken);
        assertThat(stored.getTokenHash())
                .isEqualTo(PasswordResetTokenHasher.sha256Hex(rawToken))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        assertThat(stored.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(stored.getCreatedAt()).isEqualTo(INITIAL_INSTANT);
        assertThat(stored.getExpiresAt()).isEqualTo(INITIAL_INSTANT.plus(Duration.ofMinutes(30)));
        assertThat(stored.getConsumedAt()).isNull();
        assertThat(URI.create(message.recoveryUrl()).getQuery()).isNull();
        assertThat(URI.create(message.recoveryUrl()).getFragment()).isEqualTo("token=" + rawToken);
        assertThat(message.recoveryUrl()).startsWith("https://frontend.test/reset-password#token=");
        assertThat(message.expiresAt()).isEqualTo(stored.getExpiresAt());
        assertThat(message.toString()).doesNotContain(rawToken, user.getEmail());
    }

    @Test
    void cooldownMustReuseNeutralResponseWithoutNewTokenOrEmail() throws Exception {
        User user = createEligibleProfessional(
                "cooldown.recovery@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        requestRecovery(user.getEmail());
        PasswordRecoveryInbox.awaitSize(sender, 1);
        PasswordResetToken first = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId()).get(0);
        String firstHash = first.getTokenHash();
        clock.setInstant(INITIAL_INSTANT.plusSeconds(59));

        MvcResult cooldown = requestRecovery(user.getEmail());

        assertThat(cooldown.getResponse().getStatus()).isEqualTo(202);
        assertThat(sender.messages()).hasSize(1);
        List<PasswordResetToken> tokens = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTokenHash()).isEqualTo(firstHash);
        assertThat(tokens.get(0).getConsumedAt()).isNull();
    }

    @Test
    void newRequestAfterCooldownMustInvalidatePreviousOpenTokenAndSendNewEmail() throws Exception {
        User user = createEligibleProfessional(
                "rotate.recovery@example.com",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );

        requestRecovery(user.getEmail());
        String previousRaw = rawTokenFrom(onlyMessage());
        sender.clearForTesting();
        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));

        requestRecovery(user.getEmail());

        List<PasswordResetToken> tokens = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());
        assertThat(tokens).hasSize(2);
        assertThat(tokens.get(1).getConsumedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
        assertThat(tokens.get(0).getConsumedAt()).isNull();
        assertThat(tokens.get(0).getTokenHash()).isEqualTo(PasswordResetTokenHasher.sha256Hex(rawTokenFrom(onlyMessage())));
        assertThat(tokens.get(0).getTokenHash()).isNotEqualTo(PasswordResetTokenHasher.sha256Hex(previousRaw));
        assertThat(PasswordRecoveryInbox.awaitSize(sender, 1)).hasSize(1);
    }

    private MvcResult requestRecovery(String email) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        return mockMvc.perform(post(REQUEST_ENDPOINT)
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.message").value(PasswordRecoveryAcceptedResponse.NEUTRAL_MESSAGE))
                .andReturn();
    }

    private PasswordRecoveryMessage onlyMessage() {
        List<PasswordRecoveryMessage> messages = PasswordRecoveryInbox.awaitSize(sender, 1);
        assertThat(messages).hasSize(1);
        return messages.get(0);
    }

    private static String rawTokenFrom(PasswordRecoveryMessage message) {
        String fragment = URI.create(message.recoveryUrl()).getFragment();
        assertThat(fragment).startsWith("token=");
        return fragment.substring("token=".length());
    }

    private ProfessionalProfile createEligibleProfessional(String email, ProfessionalSpecialization specialization) {
        ProfessionalProfile professional = createProfessional(email, specialization);
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        return professionalProfileRepository.saveAndFlush(professional);
    }

    private ProfessionalProfile createProfessional(String email, ProfessionalSpecialization specialization) {
        return new ProfessionalProfile(
                "Mario",
                "Rossi",
                email,
                passwordEncoder.encode(PASSWORD),
                specialization
        );
    }

    private ClientProfile createEligibleClient(String email) {
        ClientProfile client = new ClientProfile(
                "Luca",
                "Ferri",
                email,
                passwordEncoder.encode(PASSWORD),
                LocalDate.of(1996, 4, 15),
                new BigDecimal("178.00"),
                "Forza",
                Gender.MALE
        );
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        return clientProfileRepository.saveAndFlush(client);
    }

    private void cleanDatabase() {
        sender.clearForTesting();
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
