package it.zuperman.support_trainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.link.entity.ProfessionalClientLink;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

        mockMvc.perform(get("/api/v1/clients/{clientId}", Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/professionals/{professionalId}", Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
        Long professionalId = userRepository.findByEmail("professional.wrong.role@example.com")
                .orElseThrow()
                .getId();
        Long clientId = userRepository.findByEmail("client.wrong.role@example.com")
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/professionals/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/clients/{clientId}", clientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/professionals/{professionalId}", professionalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clientAId));

        mockMvc.perform(get("/api/v1/clients/{clientId}", clientAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalBToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Cliente non trovato"));
    }

    @Test
    @DisplayName("Dettaglio cliente non accessibile deve restituire un payload 404 uniforme")
    void shouldReturnUniformNotFoundForInaccessibleClientDetails() throws Exception {
        String professionalEmail = "professional.client.uniform.not-found@example.com";
        String professionalToken = registerVerifyAndLoginProfessional(
                professionalEmail,
                "Elisa",
                "Marchetti"
        );
        ProfessionalProfile professional = getProfessional(professionalEmail);

        ClientProfile neverLinkedClient = saveActiveClient("client.never-linked@example.com");
        ClientProfile inactiveLinkClient = saveActiveClient("client.inactive-link@example.com");
        ProfessionalClientLink inactiveLink = new ProfessionalClientLink(professional, inactiveLinkClient);
        inactiveLink.setActive(false);
        professionalClientLinkRepository.saveAndFlush(inactiveLink);

        ClientProfile inactiveClient = saveActiveClient("client.inactive-profile@example.com");
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(professional, inactiveClient));
        inactiveClient.setActive(false);
        userRepository.saveAndFlush(inactiveClient);

        List<Map<String, Object>> payloads = List.of(
                getComparableNotFoundPayload(
                        "/api/v1/clients/{resourceId}",
                        Long.MAX_VALUE,
                        professionalToken
                ),
                getComparableNotFoundPayload(
                        "/api/v1/clients/{resourceId}",
                        neverLinkedClient.getId(),
                        professionalToken
                ),
                getComparableNotFoundPayload(
                        "/api/v1/clients/{resourceId}",
                        inactiveLinkClient.getId(),
                        professionalToken
                ),
                getComparableNotFoundPayload(
                        "/api/v1/clients/{resourceId}",
                        inactiveClient.getId(),
                        professionalToken
                )
        );

        assertUniformNotFoundPayload(payloads, "CLIENT_NOT_FOUND", "Cliente non trovato");
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(professionalAId));

        mockMvc.perform(get("/api/v1/professionals/{professionalId}", professionalAId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientBToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Professionista non trovato"));
    }

    @Test
    @DisplayName("Dettaglio professionista non accessibile deve restituire un payload 404 uniforme")
    void shouldReturnUniformNotFoundForInaccessibleProfessionalDetails() throws Exception {
        String linkedProfessionalEmail = "professional.client-principal@example.com";
        String linkedProfessionalToken = registerVerifyAndLoginProfessional(
                linkedProfessionalEmail,
                "Lorenzo",
                "Parisi"
        );
        String clientEmail = "client.professional.uniform.not-found@example.com";
        String clientToken = registerAndLoginClient(createInvite(linkedProfessionalToken), clientEmail);
        ClientProfile client = getClient(clientEmail);

        ProfessionalProfile neverLinkedProfessional = saveActiveProfessional(
                "professional.never-linked@example.com"
        );
        ProfessionalProfile inactiveLinkProfessional = saveActiveProfessional(
                "professional.inactive-link@example.com"
        );
        ProfessionalClientLink inactiveLink = new ProfessionalClientLink(inactiveLinkProfessional, client);
        inactiveLink.setActive(false);
        professionalClientLinkRepository.saveAndFlush(inactiveLink);

        ProfessionalProfile inactiveProfessional = saveActiveProfessional(
                "professional.inactive-profile@example.com"
        );
        professionalClientLinkRepository.saveAndFlush(new ProfessionalClientLink(inactiveProfessional, client));
        inactiveProfessional.setActive(false);
        userRepository.saveAndFlush(inactiveProfessional);

        List<Map<String, Object>> payloads = List.of(
                getComparableNotFoundPayload(
                        "/api/v1/professionals/{resourceId}",
                        Long.MAX_VALUE,
                        clientToken
                ),
                getComparableNotFoundPayload(
                        "/api/v1/professionals/{resourceId}",
                        neverLinkedProfessional.getId(),
                        clientToken
                ),
                getComparableNotFoundPayload(
                        "/api/v1/professionals/{resourceId}",
                        inactiveLinkProfessional.getId(),
                        clientToken
                ),
                getComparableNotFoundPayload(
                        "/api/v1/professionals/{resourceId}",
                        inactiveProfessional.getId(),
                        clientToken
                )
        );

        assertUniformNotFoundPayload(
                payloads,
                "PROFESSIONAL_NOT_FOUND",
                "Professionista non trovato"
        );
    }

    private Map<String, Object> getComparableNotFoundPayload(
            String endpoint,
            Long resourceId,
            String token
    ) throws Exception {
        MvcResult result = mockMvc.perform(get(endpoint, resourceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andReturn();

        Map<String, Object> payload = new LinkedHashMap<>(JsonPath.read(
                result.getResponse().getContentAsString(),
                "$"
        ));
        payload.remove("timestamp");
        payload.remove("path");
        return payload;
    }

    private void assertUniformNotFoundPayload(
            List<Map<String, Object>> payloads,
            String code,
            String message
    ) {
        Map<String, Object> expectedPayload = payloads.get(0);

        assertThat(payloads).allMatch(expectedPayload::equals);
        assertThat(expectedPayload)
                .containsEntry("status", 404)
                .containsEntry("code", code)
                .containsEntry("message", message)
                .doesNotContainKeys("error", "errorCode", "validationErrors", "fieldErrors");
    }

    private ClientProfile saveActiveClient(String email) {
        ClientProfile client = new ClientProfile(
                "Cliente",
                "Test",
                email,
                PASSWORD,
                LocalDate.of(1994, 6, 15),
                new BigDecimal("175.00"),
                "Obiettivo test",
                Gender.OTHER
        );
        client.setAccountStatus(AccountStatus.ACTIVE);
        client.setEmailVerified(true);
        return userRepository.saveAndFlush(client);
    }

    private ProfessionalProfile saveActiveProfessional(String email) {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Professionista",
                "Test",
                email,
                PASSWORD,
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        return userRepository.saveAndFlush(professional);
    }

    private ClientProfile getClient(String email) {
        return (ClientProfile) userRepository.findByEmail(email).orElseThrow();
    }

    private ProfessionalProfile getProfessional(String email) {
        return (ProfessionalProfile) userRepository.findByEmail(email).orElseThrow();
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

        confirmEmail(verificationToken.getToken());

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

        User savedClient = userRepository.findByEmail(email).orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedClient.getId()))
                .findFirst()
                .orElseThrow();
        confirmEmail(verificationToken.getToken());

        return login(email, PASSWORD);
    }

    private void confirmEmail(String token) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk());
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
