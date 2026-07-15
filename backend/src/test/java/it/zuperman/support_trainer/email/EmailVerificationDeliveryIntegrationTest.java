package it.zuperman.support_trainer.email;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.adapter.InMemoryEmailVerificationSender;
import it.zuperman.support_trainer.email.model.EmailVerificationMessage;
import it.zuperman.support_trainer.email.model.EmailVerificationReason;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.MutableTestClock;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;

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
class EmailVerificationDeliveryIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String RESEND_ENDPOINT = "/api/v1/auth/email-verification/resend";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryEmailVerificationSender sender;
    @Autowired
    private MutableTestClock clock;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;
    @Autowired
    private InviteCodeRepository inviteRepository;
    @Autowired
    private ProfessionalClientLinkRepository linkRepository;

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
    void professionalRegistrationShouldDeliverOneMessageAfterCommit() throws Exception {
        String email = "professional.delivery@example.com";
        MvcResult response = registerProfessional(email);

        User professional = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken token = tokensFor(professional).get(0);
        EmailVerificationMessage message = onlyMessage();

        assertThat(professional.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(professional.getEmailVerified()).isFalse();
        assertThat(message.recipient()).isEqualTo(email);
        assertThat(message.reason()).isEqualTo(EmailVerificationReason.REGISTRATION);
        assertThat(message.expiresAt()).isEqualTo(token.getExpiresAt());
        assertThat(message.expiresAt()).isEqualTo(INITIAL_INSTANT.plus(Duration.ofHours(24)));
        assertThat(URI.create(message.verificationUrl()).getFragment()).isEqualTo("token=" + token.getToken());
        assertThat(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(token.getToken());
    }

    @Test
    void clientRegistrationShouldCommitInviteLinkAndDeliveryTogether() throws Exception {
        String professionalEmail = "professional.owner@example.com";
        registerProfessional(professionalEmail);
        User professional = userRepository.findByEmail(professionalEmail).orElseThrow();
        professional.setEmailVerified(true);
        professional.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.saveAndFlush(professional);
        String accessToken = login(professionalEmail);
        String inviteCode = createInvite(accessToken);
        sender.clearForTesting();

        String clientEmail = "client.delivery@example.com";
        MvcResult response = registerClient(inviteCode, clientEmail);

        ClientProfile client = (ClientProfile) userRepository.findByEmail(clientEmail).orElseThrow();
        EmailVerificationToken token = tokensFor(client).get(0);
        EmailVerificationMessage message = onlyMessage();
        InviteCode invite = inviteRepository.findByCode(inviteCode).orElseThrow();
        List<ProfessionalClientLink> links = linkRepository.findAllByClient_IdAndActiveTrue(client.getId());

        assertThat(invite.getUsed()).isTrue();
        assertThat(invite.getUsedAt()).isNotNull();
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getProfessional().getId()).isEqualTo(professional.getId());
        assertThat(message.recipient()).isEqualTo(clientEmail);
        assertThat(message.reason()).isEqualTo(EmailVerificationReason.REGISTRATION);
        assertThat(message.expiresAt()).isEqualTo(token.getExpiresAt());
        assertThat(URI.create(message.verificationUrl()).getFragment()).isEqualTo("token=" + token.getToken());
        assertThat(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(token.getToken());
    }

    @Test
    void resendShouldDeliverOnlyForNewEligibleTokenAndKeepUniformResponse() throws Exception {
        String email = "resend.delivery@example.com";
        registerProfessional(email);
        User professional = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken oldToken = tokensFor(professional).get(0);
        sender.clearForTesting();
        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));

        MvcResult eligibleResponse = resend(email);
        EmailVerificationToken newToken = tokensFor(professional).get(0);
        EmailVerificationMessage message = onlyMessage();

        assertThat(message.reason()).isEqualTo(EmailVerificationReason.RESEND);
        assertThat(URI.create(message.verificationUrl()).getFragment()).isEqualTo("token=" + newToken.getToken());
        assertThat(message.verificationUrl()).doesNotContain(oldToken.getToken());

        MvcResult cooldownResponse = resend(email);
        MvcResult missingResponse = resend("missing.delivery@example.com");

        ProfessionalProfile verified = new ProfessionalProfile(
                "Verified", "User", "verified.delivery@example.com", "encoded",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        verified.setAccountStatus(AccountStatus.ACTIVE);
        verified.setEmailVerified(true);
        userRepository.saveAndFlush(verified);
        MvcResult verifiedResponse = resend(verified.getEmail());

        ProfessionalProfile inactive = new ProfessionalProfile(
                "Inactive", "User", "inactive.delivery@example.com", "encoded",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        inactive.setActive(false);
        userRepository.saveAndFlush(inactive);
        MvcResult inactiveResponse = resend(inactive.getEmail());

        assertThat(sender.messages()).hasSize(1);
        String expectedPayload = eligibleResponse.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(List.of(cooldownResponse, missingResponse, verifiedResponse, inactiveResponse))
                .allSatisfy(result -> {
                    assertThat(result.getResponse().getStatus()).isEqualTo(202);
                    assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                            .isEqualTo(expectedPayload);
                });
    }

    private MvcResult registerProfessional(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Mario",
                                  "lastName":"Rossi",
                                  "email":"%s",
                                  "password":"%s",
                                  "specialization":"PERSONAL_TRAINER"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    private MvcResult registerClient(String inviteCode, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Luca",
                                  "lastName":"Ferri",
                                  "email":"%s",
                                  "password":"%s",
                                  "inviteCode":"%s",
                                  "birthDate":"1996-04-15",
                                  "heightCm":178.00,
                                  "primaryGoal":"Migliorare la forma fisica",
                                  "gender":"MALE"
                                }
                                """.formatted(email, PASSWORD, inviteCode)))
                .andExpect(status().isAccepted())
                .andReturn();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String createInvite(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private MvcResult resend(String email) throws Exception {
        return mockMvc.perform(post(RESEND_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();
    }

    private EmailVerificationMessage onlyMessage() {
        assertThat(sender.messages()).hasSize(1);
        return sender.messages().get(0);
    }

    private List<EmailVerificationToken> tokensFor(User user) {
        return tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());
    }

    private void cleanDatabase() {
        sender.clearForTesting();
        tokenRepository.deleteAll();
        linkRepository.deleteAll();
        inviteRepository.deleteAll();
        userRepository.deleteAll();
    }
}
