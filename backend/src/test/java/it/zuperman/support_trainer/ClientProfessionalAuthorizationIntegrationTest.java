package it.zuperman.support_trainer;

import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
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

    @Autowired
    private ProfessionalClientLinkRepository professionalClientLinkRepository;

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

    @Test
    @DisplayName("Professionista autenticato deve vedere solo i propri clienti")
    void shouldReturnOnlyAuthenticatedProfessionalClients() throws Exception {
        String professionalAToken = registerVerifyAndLoginProfessional(
                "professional.a.clients.list@example.com",
                "Alberto",
                "Riva"
        );
        String professionalAInviteCode = createInvite(professionalAToken);
        String clientAEmail = "client.a.clients.list@example.com";
        registerAndLoginClient(professionalAInviteCode, clientAEmail);

        String professionalBToken = registerVerifyAndLoginProfessional(
                "professional.b.clients.list@example.com",
                "Bianca",
                "Fontana"
        );
        String professionalBInviteCode = createInvite(professionalBToken);
        String clientBEmail = "client.b.clients.list@example.com";
        registerAndLoginClient(professionalBInviteCode, clientBEmail);

        Long clientAId = userRepository.findByEmail(clientAEmail).orElseThrow().getId();
        Long clientBId = userRepository.findByEmail(clientBEmail).orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalAToken)))
                .andExpect(status().isOk())
                .andReturn();

        List<Number> returnedIds = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$[*].id"
        );
        List<Long> normalizedIds = returnedIds.stream()
                .map(Number::longValue)
                .toList();

        assertThat(normalizedIds)
                .hasSize(1)
                .contains(clientAId)
                .doesNotContain(clientBId);
    }

    @Test
    @DisplayName("Cliente autenticato deve vedere solo i propri professionisti")
    void shouldReturnOnlyAuthenticatedClientProfessionals() throws Exception {
        String professionalAEmail = "professional.a.professionals.list@example.com";
        String professionalAToken = registerVerifyAndLoginProfessional(
                professionalAEmail,
                "Claudio",
                "Neri"
        );
        String professionalAInviteCode = createInvite(professionalAToken);
        String clientAToken = registerAndLoginClient(
                professionalAInviteCode,
                "client.a.professionals.list@example.com"
        );

        String professionalBEmail = "professional.b.professionals.list@example.com";
        String professionalBToken = registerVerifyAndLoginProfessional(
                professionalBEmail,
                "Daniela",
                "Costa"
        );
        String professionalBInviteCode = createInvite(professionalBToken);
        registerAndLoginClient(
                professionalBInviteCode,
                "client.b.professionals.list@example.com"
        );

        Long professionalAId = userRepository.findByEmail(professionalAEmail).orElseThrow().getId();
        Long professionalBId = userRepository.findByEmail(professionalBEmail).orElseThrow().getId();

        MvcResult result = mockMvc.perform(get("/api/v1/professionals/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientAToken)))
                .andExpect(status().isOk())
                .andReturn();

        List<Number> returnedIds = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$[*].id"
        );
        List<Long> normalizedIds = returnedIds.stream()
                .map(Number::longValue)
                .toList();

        assertThat(normalizedIds)
                .hasSize(1)
                .contains(professionalAId)
                .doesNotContain(professionalBId);
    }

    @Test
    @DisplayName("Collegamento inattivo deve essere escluso dalla lista clienti del professionista")
    void shouldExcludeInactiveLinkFromProfessionalClientList() throws Exception {
        String professionalEmail = "professional.inactive.client.link@example.com";
        String professionalToken = registerVerifyAndLoginProfessional(
                professionalEmail,
                "Elena",
                "Galli"
        );
        String inviteCode = createInvite(professionalToken);
        String clientEmail = "client.inactive.client.link@example.com";
        registerAndLoginClient(inviteCode, clientEmail);

        Long professionalId = userRepository.findByEmail(professionalEmail).orElseThrow().getId();
        Long clientId = userRepository.findByEmail(clientEmail).orElseThrow().getId();
        ProfessionalClientLink link = professionalClientLinkRepository
                .findAllByProfessional_IdAndActiveTrue(professionalId)
                .stream()
                .filter(candidate -> candidate.getClient().getId().equals(clientId))
                .findFirst()
                .orElseThrow();

        link.setActive(false);
        professionalClientLinkRepository.saveAndFlush(link);

        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Collegamento inattivo deve essere escluso dalla lista professionisti del cliente")
    void shouldExcludeInactiveLinkFromClientProfessionalList() throws Exception {
        String professionalEmail = "professional.inactive.professional.link@example.com";
        String professionalToken = registerVerifyAndLoginProfessional(
                professionalEmail,
                "Fabio",
                "Monti"
        );
        String inviteCode = createInvite(professionalToken);
        String clientEmail = "client.inactive.professional.link@example.com";
        String clientToken = registerAndLoginClient(inviteCode, clientEmail);

        Long professionalId = userRepository.findByEmail(professionalEmail).orElseThrow().getId();
        Long clientId = userRepository.findByEmail(clientEmail).orElseThrow().getId();
        ProfessionalClientLink link = professionalClientLinkRepository
                .findAllByProfessional_IdAndActiveTrue(professionalId)
                .stream()
                .filter(candidate -> candidate.getClient().getId().equals(clientId))
                .findFirst()
                .orElseThrow();

        link.setActive(false);
        professionalClientLinkRepository.saveAndFlush(link);

        mockMvc.perform(get("/api/v1/professionals/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Professionista deve accedere solo al dettaglio dei clienti collegati")
    void shouldEnforceClientDetailOwnershipForProfessional() throws Exception {
        String professionalAToken = registerVerifyAndLoginProfessional(
                "professional.a.client.detail@example.com",
                "Andrea",
                "Villa"
        );
        String professionalAInviteCode = createInvite(professionalAToken);
        String clientAEmail = "client.a.client.detail@example.com";
        registerAndLoginClient(professionalAInviteCode, clientAEmail);

        String professionalBToken = registerVerifyAndLoginProfessional(
                "professional.b.client.detail@example.com",
                "Beatrice",
                "Leone"
        );
        String professionalBInviteCode = createInvite(professionalBToken);
        registerAndLoginClient(
                professionalBInviteCode,
                "client.b.client.detail@example.com"
        );

        Long clientAId = userRepository.findByEmail(clientAEmail).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/clients/{clientId}", clientAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalAToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/clients/{clientId}", clientAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Cliente deve accedere solo al dettaglio dei professionisti collegati")
    void shouldEnforceProfessionalDetailOwnershipForClient() throws Exception {
        String professionalAEmail = "professional.a.professional.detail@example.com";
        String professionalAToken = registerVerifyAndLoginProfessional(
                professionalAEmail,
                "Carlo",
                "Ferri"
        );
        String professionalAInviteCode = createInvite(professionalAToken);
        String clientAToken = registerAndLoginClient(
                professionalAInviteCode,
                "client.a.professional.detail@example.com"
        );

        String professionalBToken = registerVerifyAndLoginProfessional(
                "professional.b.professional.detail@example.com",
                "Diana",
                "Greco"
        );
        String professionalBInviteCode = createInvite(professionalBToken);
        String clientBToken = registerAndLoginClient(
                professionalBInviteCode,
                "client.b.professional.detail@example.com"
        );

        Long professionalAId = userRepository.findByEmail(professionalAEmail).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/professionals/{professionalId}", professionalAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientAToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/professionals/{professionalId}", professionalAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientBToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PROFESSIONAL_ACCESS_DENIED"));
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
