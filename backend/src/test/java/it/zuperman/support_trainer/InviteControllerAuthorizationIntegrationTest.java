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
import org.springframework.http.HttpHeaders;
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
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
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
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cliente autenticato non deve accedere agli endpoint Invite")
    void shouldRejectAuthenticatedClientForInviteEndpoints() throws Exception {
        String professionalToken = registerVerifyAndLoginProfessional(
                "professional.client.authorization@example.com",
                "Paolo",
                "Serra"
        );
        String inviteCode = createInvite(professionalToken);
        String clientToken = registerAndLoginClient(inviteCode);

        mockMvc.perform(get("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Professionista attivo e verificato deve accedere agli endpoint Invite")
    void shouldAllowActiveVerifiedProfessionalForInviteEndpoints() throws Exception {
        String professionalToken = registerVerifyAndLoginProfessional(
                "professional.invite.authorization@example.com",
                "Giulia",
                "Marini"
        );

        mockMvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Professionista autenticato deve vedere solo i propri inviti")
    void shouldReturnOnlyAuthenticatedProfessionalInvites() throws Exception {
        String professionalAToken = registerVerifyAndLoginProfessional(
                "professional.a.invite.ownership@example.com",
                "Alessandro",
                "Villa"
        );
        String professionalBToken = registerVerifyAndLoginProfessional(
                "professional.b.invite.ownership@example.com",
                "Beatrice",
                "Leone"
        );

        String professionalAInviteCode = createInvite(professionalAToken);
        String professionalBInviteCode = createInvite(professionalBToken);

        MvcResult result = mockMvc.perform(get("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalAToken)))
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

    private String registerVerifyAndLoginProfessional(
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

        return login(email, PASSWORD);
    }

    private String createInvite(String professionalToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isCreated())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private String registerAndLoginClient(String inviteCode) throws Exception {
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

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequestBody))
                .andExpect(status().isCreated());

        return login(email, PASSWORD);
    }

    private String login(String email, String password) throws Exception {
        String loginRequestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
