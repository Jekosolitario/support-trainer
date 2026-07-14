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
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class ClientEmailVerificationIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String CONFIRM_ENDPOINT = "/api/v1/auth/email-verification/confirm";

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
    private ApplicationTimeProvider timeProvider;

    @Test
    @DisplayName("Cliente pending resta nascosto e diventa operativo solo dopo la conferma")
    void shouldActivateClientAndExposeExistingLinkOnlyAfterConfirmation() throws Exception {
        String professionalEmail = "professional.client-verification@example.com";
        String clientEmail = "client.pending-verification@example.com";
        String professionalToken = registerVerifyAndLoginProfessional(professionalEmail);
        String inviteCode = createInvite(professionalToken);
        Instant beforeRegistration = timeProvider.nowInstant();

        registerClient(inviteCode, clientEmail);

        Instant afterRegistration = timeProvider.nowInstant();
        ClientProfile client = (ClientProfile) userRepository.findByEmail(clientEmail).orElseThrow();
        Long professionalId = userRepository.findByEmail(professionalEmail).orElseThrow().getId();
        EmailVerificationToken clientVerificationToken = findTokenFor(client);
        InviteCode consumedInvite = inviteCodeRepository.findByCode(inviteCode).orElseThrow();
        Instant inviteUsedAt = consumedInvite.getUsedAt();

        assertThat(client.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(client.getEmailVerified()).isFalse();
        assertThat(client.getActive()).isTrue();
        assertThat(clientVerificationToken.getUsed()).isFalse();
        assertThat(clientVerificationToken.getExpiresAt())
                .isAfterOrEqualTo(beforeRegistration.plusSeconds(86_400))
                .isBeforeOrEqualTo(afterRegistration.plusSeconds(86_400));
        assertThat(professionalClientLinkRepository
                .existsByProfessional_IdAndClient_IdAndActiveTrue(professionalId, client.getId()))
                .isTrue();
        assertThat(consumedInvite.getUsed()).isTrue();
        assertThat(inviteUsedAt).isNotNull();

        assertLoginForbidden(clientEmail);
        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/clients/{clientId}", client.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_NOT_FOUND"));

        confirmEmail(clientVerificationToken.getToken());
        Instant firstUsedAt = clientVerificationToken.getUsedAt();

        assertThat(client.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(client.getEmailVerified()).isTrue();
        assertThat(clientVerificationToken.getUsed()).isTrue();
        assertThat(firstUsedAt).isNotNull();
        assertThat(inviteCodeRepository.findByCode(inviteCode).orElseThrow().getUsedAt())
                .isEqualTo(inviteUsedAt);

        String clientToken = login(clientEmail);
        assertThat(clientToken).isNotBlank();
        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(client.getId()));
        mockMvc.perform(get("/api/v1/clients/{clientId}", client.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(client.getId()));

        confirmEmail(clientVerificationToken.getToken());
        assertThat(clientVerificationToken.getUsedAt()).isEqualTo(firstUsedAt);
        assertThat(emailVerificationTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(client.getId())))
                .hasSize(1);

        registerClientExpectingUsedInvite(inviteCode, "second.client@example.com");
    }

    @Test
    @DisplayName("Token cliente scaduto non attiva il cliente e non modifica l'invito consumato")
    void shouldKeepClientPendingAndInviteConsumedWhenTokenIsExpired() throws Exception {
        String professionalToken = registerVerifyAndLoginProfessional("professional.expired-client@example.com");
        String inviteCode = createInvite(professionalToken);
        String clientEmail = "client.expired-verification@example.com";
        registerClient(inviteCode, clientEmail);

        ClientProfile client = (ClientProfile) userRepository.findByEmail(clientEmail).orElseThrow();
        EmailVerificationToken token = findTokenFor(client);
        InviteCode invite = inviteCodeRepository.findByCode(inviteCode).orElseThrow();
        Instant inviteUsedAt = invite.getUsedAt();
        token.setExpiresAt(timeProvider.nowInstant());
        emailVerificationTokenRepository.saveAndFlush(token);

        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token.getToken())))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_VERIFICATION_TOKEN_EXPIRED"));

        assertThat(client.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(client.getEmailVerified()).isFalse();
        assertThat(token.getUsed()).isFalse();
        assertThat(inviteCodeRepository.findByCode(inviteCode).orElseThrow().getUsed()).isTrue();
        assertThat(inviteCodeRepository.findByCode(inviteCode).orElseThrow().getUsedAt())
                .isEqualTo(inviteUsedAt);
        assertLoginForbidden(clientEmail);
    }

    @Test
    @DisplayName("Fallimento dopo il lock dell'invito non lascia cliente, link o token e non consuma l'invito")
    void shouldRollbackClientRegistrationWhenProfessionalBecomesInactive() throws Exception {
        String professionalEmail = "professional.rollback-client@example.com";
        String professionalToken = registerVerifyAndLoginProfessional(professionalEmail);
        String inviteCode = createInvite(professionalToken);
        ProfessionalProfile professional = (ProfessionalProfile) userRepository
                .findByEmail(professionalEmail)
                .orElseThrow();
        professional.setActive(false);
        userRepository.saveAndFlush(professional);
        long linksBefore = professionalClientLinkRepository.count();
        long tokensBefore = emailVerificationTokenRepository.count();
        String clientEmail = "client.rollback-registration@example.com";

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientRegistrationBody(inviteCode, clientEmail)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PROFESSIONAL_NOT_ACTIVE"));

        InviteCode invite = inviteCodeRepository.findByCode(inviteCode).orElseThrow();
        assertThat(invite.getUsed()).isFalse();
        assertThat(invite.getUsedAt()).isNull();
        assertThat(userRepository.findByEmail(clientEmail)).isEmpty();
        assertThat(professionalClientLinkRepository.count()).isEqualTo(linksBefore);
        assertThat(emailVerificationTokenRepository.count()).isEqualTo(tokensBefore);
    }

    private String registerVerifyAndLoginProfessional(String email) throws Exception {
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

        User professional = userRepository.findByEmail(email).orElseThrow();
        confirmEmail(findTokenFor(professional).getToken());
        return login(email);
    }

    private String createInvite(String professionalToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private void registerClient(String inviteCode, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientRegistrationBody(inviteCode, email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty());
    }

    private void registerClientExpectingUsedInvite(String inviteCode, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientRegistrationBody(inviteCode, email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVITE_CODE_ALREADY_USED"));
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    private String clientRegistrationBody(String inviteCode, String email) {
        return """
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
                """.formatted(email, PASSWORD, inviteCode);
    }

    private void assertLoginForbidden(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_ACTIVE"));
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String loginBody(String email) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
    }

    private void confirmEmail(String token) throws Exception {
        mockMvc.perform(post(CONFIRM_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verificata correttamente"));
    }

    private String tokenBody(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }

    private EmailVerificationToken findTokenFor(User user) {
        return emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
