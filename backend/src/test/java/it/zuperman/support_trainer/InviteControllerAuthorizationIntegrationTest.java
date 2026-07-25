package it.zuperman.support_trainer;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class InviteControllerAuthorizationIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    @DisplayName("Utente anonimo non deve accedere agli endpoint Invite")
    void shouldRejectAnonymousUserForInviteEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/invites"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/invites"))
                .andExpect(status().isForbidden());

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cliente autenticato non deve accedere agli endpoint Invite")
    void shouldRejectAuthenticatedClientForInviteEndpoints() throws Exception {
        CsrfSession professionalAuth = registerVerifyAndLoginProfessional(
                "professional.client.authorization@example.com",
                "Paolo",
                "Serra"
        );
        String inviteCode = createInvite(professionalAuth);
        CsrfSession clientAuth = registerAndLoginClient(inviteCode);

        mockMvc.perform(get("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSession(clientAuth)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(clientAuth)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professionista attivo e verificato deve accedere agli endpoint Invite")
    void shouldAllowActiveVerifiedProfessionalForInviteEndpoints() throws Exception {
        CsrfSession professionalAuth = registerVerifyAndLoginProfessional(
                "professional.invite.authorization@example.com",
                "Giulia",
                "Marini"
        );

        mockMvc.perform(post("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(professionalAuth)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSession(professionalAuth)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Professionista autenticato deve vedere solo i propri inviti")
    void shouldReturnOnlyAuthenticatedProfessionalInvites() throws Exception {
        CsrfSession professionalAAuth = registerVerifyAndLoginProfessional(
                "professional.a.invite.ownership@example.com",
                "Alessandro",
                "Villa"
        );
        CsrfSession professionalBAuth = registerVerifyAndLoginProfessional(
                "professional.b.invite.ownership@example.com",
                "Beatrice",
                "Leone"
        );

        String professionalAInviteCode = createInvite(professionalAAuth);
        String professionalBInviteCode = createInvite(professionalBAuth);

        MvcResult result = mockMvc.perform(get("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSession(professionalAAuth)))
                .andExpect(status().isOk())
                .andReturn();

        List<String> returnedInviteCodes = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$[*].code"
        );

        assertThat(returnedInviteCodes)
                .hasSize(1)
                .contains(professionalAInviteCode)
                .doesNotContain(professionalBInviteCode);
    }

    private CsrfSession registerVerifyAndLoginProfessional(
            String email,
            String firstName,
            String lastName
    ) throws Exception {
        String registrationRequestBody = """
                {
                  "firstName": "%s",
                  "lastName": "%s",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(firstName, lastName, email, PASSWORD);

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
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

        return SessionAuthTestSupport.loginAndRefreshCsrf(mockMvc, email, PASSWORD);
    }

    private String createInvite(CsrfSession professionalAuth) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(professionalAuth)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private CsrfSession registerAndLoginClient(String inviteCode) throws Exception {
        String email = "client.invite.authorization@example.com";
        String registrationRequestBody = """
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

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/register/client")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestBody))
                .andExpect(status().isAccepted());

        User savedClient = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedClient.getId()))
                .findFirst()
                .orElseThrow();
        confirmEmail(verificationToken.getToken());

        return SessionAuthTestSupport.loginAndRefreshCsrf(mockMvc, email, PASSWORD);
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
}
