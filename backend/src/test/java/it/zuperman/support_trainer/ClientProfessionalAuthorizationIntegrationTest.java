package it.zuperman.support_trainer;

import com.jayway.jsonpath.JsonPath;
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
class ClientProfessionalAuthorizationIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    @DisplayName("Utente anonimo non deve accedere agli endpoint Client e Professional")
    void shouldRejectAnonymousUserForClientAndProfessionalEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/clients/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/professionals/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Utente autenticato con ruolo errato non deve accedere agli endpoint")
    void shouldRejectAuthenticatedUserWithWrongRole() throws Exception {
        String professionalToken = registerVerifyAndLoginProfessional(
                "professional.wrong.role@example.com",
                "Paolo",
                "Serra"
        );
        String inviteCode = createInvite(professionalToken);
        String clientToken = registerAndLoginClient(
                inviteCode,
                "client.wrong.role@example.com"
        );

        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/professionals/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Utente autenticato con ruolo corretto deve accedere agli endpoint")
    void shouldAllowAuthenticatedUserWithCorrectRole() throws Exception {
        String professionalToken = registerVerifyAndLoginProfessional(
                "professional.correct.role@example.com",
                "Giulia",
                "Marini"
        );
        String inviteCode = createInvite(professionalToken);
        String clientToken = registerAndLoginClient(
                inviteCode,
                "client.correct.role@example.com"
        );

        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/professionals/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk());
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

    private String registerAndLoginClient(String inviteCode, String email) throws Exception {
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
