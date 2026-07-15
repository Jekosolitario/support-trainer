package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AuthControllerEmailResendIntegrationTest.FixedClockConfiguration.class)
@Transactional
class AuthControllerEmailResendIntegrationTest {

    private static final Instant INITIAL_INSTANT = Instant.parse("2026-07-14T12:00:00Z");
    private static final String PASSWORD = "Password123!";
    private static final String RESEND_ENDPOINT = "/api/v1/auth/email-verification/resend";
    private static final String CONFIRM_ENDPOINT = "/api/v1/auth/email-verification/confirm";
    private static final String RESEND_MESSAGE
            = "Se l'indirizzo è associato a un account da verificare, riceverai le istruzioni necessarie";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    @Autowired
    private ProfessionalClientLinkRepository professionalClientLinkRepository;

    @Autowired
    private MutableFixedClock clock;

    @BeforeEach
    void resetClock() {
        clock.setInstant(INITIAL_INSTANT);
    }

    @Test
    @DisplayName("Professional pending può reinviare al boundary e usare solo il nuovo token")
    void shouldResendForPendingProfessionalAtCooldownBoundary() throws Exception {
        String email = "professional.resend@example.com";
        registerProfessional(email);
        User professional = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken previousToken = findTokensFor(professional).get(0);

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        MvcResult resendResult = resend("  PROFESSIONAL.RESEND@EXAMPLE.COM  ");

        List<EmailVerificationToken> tokens = findTokensFor(professional);
        EmailVerificationToken currentToken = tokens.get(0);
        assertThat(tokens).hasSize(2);
        assertThat(tokens.stream().filter(token -> Boolean.FALSE.equals(token.getUsed()))).hasSize(1);
        assertThat(previousToken.getUsed()).isTrue();
        assertThat(previousToken.getUsedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
        assertThat(currentToken.getUsed()).isFalse();
        assertThat(currentToken.getCreatedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
        assertThat(currentToken.getExpiresAt())
                .isEqualTo(INITIAL_INSTANT.plusSeconds(60).plus(Duration.ofHours(24)));
        assertThat(UUID.fromString(currentToken.getToken()).version()).isEqualTo(4);
        assertThat(resendResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(currentToken.getToken(), email);

        confirmExpectingAlreadyUsed(previousToken.getToken());
        assertThat(professional.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(professional.getEmailVerified()).isFalse();

        confirm(currentToken.getToken());
        assertThat(professional.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(professional.getEmailVerified()).isTrue();
        assertThat(login(email)).isNotBlank();
    }

    @Test
    @DisplayName("Client pending conserva invito e link durante il reinvio")
    void shouldResendForPendingClientWithoutChangingInviteOrLink() throws Exception {
        String professionalEmail = "professional.client-resend@example.com";
        registerProfessional(professionalEmail);
        User professional = userRepository.findByEmail(professionalEmail).orElseThrow();
        confirm(findTokensFor(professional).get(0).getToken());
        String professionalAccessToken = login(professionalEmail);
        String inviteCodeValue = createInvite(professionalAccessToken);
        String clientEmail = "client.resend@example.com";
        registerClient(inviteCodeValue, clientEmail);

        ClientProfile client = (ClientProfile) userRepository.findByEmail(clientEmail).orElseThrow();
        EmailVerificationToken previousToken = findTokensFor(client).get(0);
        InviteCode inviteBefore = inviteCodeRepository.findByCode(inviteCodeValue).orElseThrow();
        Instant inviteUsedAt = inviteBefore.getUsedAt();
        ProfessionalClientLink linkBefore = professionalClientLinkRepository
                .findAllByClient_IdAndActiveTrue(client.getId())
                .get(0);
        Long linkId = linkBefore.getId();
        long linkCount = professionalClientLinkRepository.count();

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        resend(clientEmail);

        List<EmailVerificationToken> tokens = findTokensFor(client);
        EmailVerificationToken currentToken = tokens.get(0);
        InviteCode inviteAfter = inviteCodeRepository.findByCode(inviteCodeValue).orElseThrow();
        ProfessionalClientLink linkAfter = professionalClientLinkRepository.findById(linkId).orElseThrow();
        assertThat(tokens).hasSize(2);
        assertThat(previousToken.getUsed()).isTrue();
        assertThat(currentToken.getUsed()).isFalse();
        assertThat(inviteAfter.getUsed()).isTrue();
        assertThat(inviteAfter.getUsedAt()).isEqualTo(inviteUsedAt);
        assertThat(inviteAfter.getActive()).isEqualTo(inviteBefore.getActive());
        assertThat(linkAfter.getActive()).isTrue();
        assertThat(linkAfter.getProfessional().getId()).isEqualTo(professional.getId());
        assertThat(linkAfter.getClient().getId()).isEqualTo(client.getId());
        assertThat(professionalClientLinkRepository.count()).isEqualTo(linkCount);

        assertClientHidden(professionalAccessToken, client.getId());
        confirm(currentToken.getToken());
        assertThat(login(clientEmail)).isNotBlank();
        assertClientVisible(professionalAccessToken, client.getId());
    }

    @Test
    @DisplayName("Cooldown blocca a 59,999999 secondi e riparte dopo ogni token")
    void shouldEnforceExactCooldownAndRestartItAfterResend() throws Exception {
        String email = "cooldown.resend@example.com";
        registerProfessional(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken registrationToken = findTokensFor(user).get(0);

        clock.setInstant(INITIAL_INSTANT.plusSeconds(59).plusNanos(999_999_000));
        resend(email);
        assertThat(findTokensFor(user)).containsExactly(registrationToken);
        assertThat(registrationToken.getUsed()).isFalse();
        assertThat(registrationToken.getUsedAt()).isNull();

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        resend(email);
        List<EmailVerificationToken> afterFirstResend = findTokensFor(user);
        EmailVerificationToken secondToken = afterFirstResend.get(0);
        assertThat(afterFirstResend).hasSize(2);
        assertThat(registrationToken.getUsedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));

        clock.setInstant(INITIAL_INSTANT.plusSeconds(119).plusNanos(999_999_000));
        resend(email);
        assertThat(findTokensFor(user)).hasSize(2);
        assertThat(secondToken.getUsed()).isFalse();
        assertThat(secondToken.getUsedAt()).isNull();
        assertThat(registrationToken.getUsedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));

        clock.setInstant(INITIAL_INSTANT.plusSeconds(120));
        resend(email);
        List<EmailVerificationToken> afterSecondResend = findTokensFor(user);
        assertThat(afterSecondResend).hasSize(3);
        assertThat(afterSecondResend.stream().filter(token -> Boolean.FALSE.equals(token.getUsed()))).hasSize(1);
        assertThat(secondToken.getUsedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(120));
        assertThat(registrationToken.getUsedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
    }

    @Test
    @DisplayName("Status e payload sono identici per tutti gli esiti validi")
    void shouldReturnIdenticalResponseWithoutAccountEnumeration() throws Exception {
        String pendingProfessionalEmail = "pending.uniform@example.com";
        registerProfessional(pendingProfessionalEmail);

        String ownerEmail = "owner.uniform@example.com";
        registerProfessional(ownerEmail);
        User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        confirm(findTokensFor(owner).get(0).getToken());
        String ownerAccessToken = login(ownerEmail);
        String pendingClientEmail = "client.uniform@example.com";
        registerClient(createInvite(ownerAccessToken), pendingClientEmail);

        String verifiedEmail = "verified.uniform@example.com";
        registerProfessional(verifiedEmail);
        User verified = userRepository.findByEmail(verifiedEmail).orElseThrow();
        confirm(findTokensFor(verified).get(0).getToken());

        String inactiveEmail = "inactive.uniform@example.com";
        registerProfessional(inactiveEmail);
        ProfessionalProfile inactive = (ProfessionalProfile) userRepository.findByEmail(inactiveEmail).orElseThrow();
        inactive.setActive(false);
        userRepository.saveAndFlush(inactive);

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        String cooldownEmail = "cooldown.uniform@example.com";
        registerProfessional(cooldownEmail);

        long verifiedTokenCount = findTokensFor(verified).size();
        EmailVerificationToken inactiveToken = findTokensFor(inactive).get(0);
        List<MvcResult> results = List.of(
                resend("missing.uniform@example.com"),
                resend(pendingProfessionalEmail),
                resend(pendingClientEmail),
                resend(verifiedEmail),
                resend(inactiveEmail),
                resend(cooldownEmail)
        );

        String expectedPayload = results.get(0).getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.getResponse().getStatus()).isEqualTo(202);
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .isEqualTo(expectedPayload);
        });
        assertThat(findTokensFor(userRepository.findByEmail(pendingProfessionalEmail).orElseThrow())).hasSize(2);
        assertThat(findTokensFor(userRepository.findByEmail(pendingClientEmail).orElseThrow())).hasSize(2);
        assertThat(findTokensFor(verified)).hasSize((int) verifiedTokenCount);
        assertThat(findTokensFor(inactive)).containsExactly(inactiveToken);
        assertThat(inactiveToken.getUsed()).isFalse();
        assertThat(findTokensFor(userRepository.findByEmail(cooldownEmail).orElseThrow())).hasSize(1);
    }

    @Test
    @DisplayName("Stato incoerente restituisce 202 senza mutazioni")
    void shouldNotMutateInconsistentAccountState() throws Exception {
        String email = "inconsistent.resend@example.com";
        registerProfessional(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token = findTokensFor(user).get(0);
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);

        String activeButUnverifiedEmail = "active-unverified.resend@example.com";
        registerProfessional(activeButUnverifiedEmail);
        User activeButUnverified = userRepository.findByEmail(activeButUnverifiedEmail).orElseThrow();
        EmailVerificationToken activeButUnverifiedToken = findTokensFor(activeButUnverified).get(0);
        activeButUnverified.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.saveAndFlush(activeButUnverified);

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        resend(email);
        resend(activeButUnverifiedEmail);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(findTokensFor(user)).containsExactly(token);
        assertThat(token.getUsed()).isFalse();
        assertThat(token.getUsedAt()).isNull();
        assertThat(activeButUnverified.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(activeButUnverified.getEmailVerified()).isFalse();
        assertThat(findTokensFor(activeButUnverified)).containsExactly(activeButUnverifiedToken);
        assertThat(activeButUnverifiedToken.getUsed()).isFalse();
        assertThat(activeButUnverifiedToken.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("Reinvio invalida tutti i token non usati senza alterare quelli già usati")
    void shouldInvalidateAllUnusedTokensAndPreserveAlreadyUsedTokenTimestamp() throws Exception {
        String email = "multiple.tokens.resend@example.com";
        registerProfessional(email);
        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken registrationToken = findTokensFor(user).get(0);
        EmailVerificationToken secondUnusedToken = emailVerificationTokenRepository.saveAndFlush(
                new EmailVerificationToken(
                        user,
                        UUID.randomUUID().toString(),
                        INITIAL_INSTANT.plus(Duration.ofHours(24))
                )
        );
        EmailVerificationToken alreadyUsedToken = new EmailVerificationToken(
                user,
                UUID.randomUUID().toString(),
                INITIAL_INSTANT.plus(Duration.ofHours(24))
        );
        Instant originalUsedAt = INITIAL_INSTANT.minusSeconds(30);
        alreadyUsedToken.markAsUsed(originalUsedAt);
        emailVerificationTokenRepository.saveAndFlush(alreadyUsedToken);

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        resend(email);

        List<EmailVerificationToken> tokens = findTokensFor(user);
        assertThat(tokens).hasSize(4);
        assertThat(tokens.stream().filter(token -> Boolean.FALSE.equals(token.getUsed()))).hasSize(1);
        assertThat(registrationToken.getUsedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
        assertThat(secondUnusedToken.getUsedAt()).isEqualTo(INITIAL_INSTANT.plusSeconds(60));
        assertThat(alreadyUsedToken.getUsedAt()).isEqualTo(originalUsedAt);
    }

    @Test
    @DisplayName("Body assente e JSON malformato restituiscono 400")
    void shouldRejectMissingOrMalformedBody() throws Exception {
        mockMvc.perform(post(RESEND_ENDPOINT).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post(RESEND_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("Email assente, null, blank, invalida o troppo lunga restituisce 400")
    void shouldValidateEmailBody() throws Exception {
        assertInvalidEmail("{}");
        assertInvalidEmail("{\"email\":null}");
        assertInvalidEmail("{\"email\":\"\"}");
        assertInvalidEmail("{\"email\":\"   \"}");
        assertInvalidEmail("{\"email\":\"not-an-email\"}");
        assertInvalidEmail(emailBody("a".repeat(89) + "@example.com"));
    }

    @Test
    @DisplayName("Content-Type non supportato restituisce 415")
    void shouldRejectUnsupportedContentType() throws Exception {
        mockMvc.perform(post(RESEND_ENDPOINT)
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<email>user@example.com</email>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    private MvcResult resend(String email) throws Exception {
        return mockMvc.perform(post(RESEND_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(RESEND_MESSAGE))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.accountStatus").doesNotExist())
                .andReturn();
    }

    private void registerProfessional(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Mario",
                                  "lastName": "Rossi",
                                  "email": "%s",
                                  "password": "%s",
                                  "specialization": "PERSONAL_TRAINER"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private void registerClient(String inviteCode, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Luca",
                                  "lastName": "Ferri",
                                  "email": "%s",
                                  "password": "%s",
                                  "birthDate": "1996-04-15",
                                  "heightCm": 178.00,
                                  "primaryGoal": "Migliorare la forma fisica",
                                  "gender": "MALE",
                                  "inviteCode": "%s"
                                }
                                """.formatted(email, PASSWORD, inviteCode)))
                .andExpect(status().isCreated());
    }

    private String createInvite(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private void confirm(String token) throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verificata correttamente"));
    }

    private void confirmExpectingAlreadyUsed(String token) throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_ALREADY_USED"));
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void assertClientHidden(String professionalToken, Long clientId) throws Exception {
        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/clients/{clientId}", clientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
    }

    private void assertClientVisible(String professionalToken, Long clientId) throws Exception {
        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(clientId));
        mockMvc.perform(get("/api/v1/clients/{clientId}", clientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientId));
    }

    private void assertInvalidEmail(String body) throws Exception {
        mockMvc.perform(post(RESEND_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").isNotEmpty());
    }

    private List<EmailVerificationToken> findTokensFor(User user) {
        return emailVerificationTokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());
    }

    private String emailBody(String email) {
        return """
                {"email":"%s"}
                """.formatted(email);
    }

    private String tokenBody(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        MutableFixedClock testApplicationClock() {
            return new MutableFixedClock(Clock.fixed(INITIAL_INSTANT, ZoneOffset.UTC));
        }
    }

    static final class MutableFixedClock extends Clock {

        private volatile Clock delegate;

        private MutableFixedClock(Clock delegate) {
            this.delegate = delegate;
        }

        void setInstant(Instant instant) {
            delegate = Clock.fixed(instant, ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return delegate.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(delegate.instant(), zone);
        }

        @Override
        public Instant instant() {
            return delegate.instant();
        }
    }
}
