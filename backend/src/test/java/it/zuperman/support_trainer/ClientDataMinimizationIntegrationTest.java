package it.zuperman.support_trainer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional
class ClientDataMinimizationIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final Set<String> SUMMARY_FIELDS = Set.of(
            "id",
            "firstName",
            "lastName",
            "profileImageUrl"
    );
    private static final Set<String> DETAIL_FIELDS = Set.of(
            "id",
            "firstName",
            "lastName",
            "profileImageUrl",
            "primaryGoal"
    );
    private static final Set<String> EXCLUDED_PROFESSIONAL_FIELDS = Set.of(
            "operationalStatus",
            "active",
            "birthDate",
            "heightCm",
            "gender",
            "medicalNotes",
            "injuryNotes",
            "notes",
            "email",
            "accountStatus",
            "emailVerified",
            "createdAt",
            "updatedAt"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @ParameterizedTest(name = "{0} riceve il contratto cliente minimizzato")
    @EnumSource(ProfessionalSpecialization.class)
    void shouldReturnTheSameMinimizedClientContractToEveryProfessionalSpecialization(
            ProfessionalSpecialization specialization
    ) throws Exception {
        String suffix = specialization.name().toLowerCase().replace('_', '-')
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        String professionalEmail = "professional-" + suffix + "@test.com";
        String professionalToken = registerVerifyAndLoginProfessional(
                professionalEmail,
                specialization
        );
        String inviteCode = createInvite(professionalToken);
        String clientEmail = "client-" + suffix + "@test.com";
        registerAndLoginClient(inviteCode, clientEmail);
        Long clientId = userRepository.findByEmail(clientEmail).orElseThrow().getId();

        MvcResult listResult = mockMvc.perform(get("/api/v1/clients/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> summary = JsonPath.read(
                listResult.getResponse().getContentAsString(),
                "$[0]"
        );
        assertSummaryContract(summary);

        MvcResult detailResult = mockMvc.perform(get("/api/v1/clients/{clientId}", clientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> detail = readObject(detailResult);
        assertDetailContract(detail);
        assertThat(detail.get("primaryGoal")).isEqualTo("Migliorare la forma fisica");
    }

    @Test
    @DisplayName("Il profilo owner mantiene dati e note senza esporli negli endpoint Clients")
    void shouldPreserveOwnerProfileAndHideItsSensitiveFieldsFromProfessionalEndpoints() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String professionalEmail = "professional-owner-profile-" + suffix + "@test.com";
        String professionalToken = registerVerifyAndLoginProfessional(
                professionalEmail,
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        String inviteCode = createInvite(professionalToken);
        String clientEmail = "client-owner-profile-" + suffix + "@test.com";
        String clientToken = registerAndLoginClient(inviteCode, clientEmail);
        Long clientId = userRepository.findByEmail(clientEmail).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("ATTIVO"))
                .andExpect(jsonPath("$.birthDate").value("1996-04-15"))
                .andExpect(jsonPath("$.heightCm").value(178.00))
                .andExpect(jsonPath("$.primaryGoal").value("Migliorare la forma fisica"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.medicalNotes").value("Controllo medico periodico"))
                .andExpect(jsonPath("$.injuryNotes").value("Distorsione caviglia pregressa"))
                .andExpect(jsonPath("$.notes").value("Preferisce sessioni mattutine"));

        String updateRequestBody = """
                {
                  "heightCm": 175.50,
                  "medicalNotes": "  Controllo aggiornato  ",
                  "injuryNotes": "   "
                }
                """;

        MvcResult updateResult = mockMvc.perform(patch("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestBody))
                .andExpect(status().isOk())
                .andReturn();

        assertUpdatedOwnerProfile(readObject(updateResult));

        MvcResult persistedProfileResult = mockMvc.perform(get("/api/v1/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(clientToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertUpdatedOwnerProfile(readObject(persistedProfileResult));

        MvcResult detailResult = mockMvc.perform(get("/api/v1/clients/{clientId}", clientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(professionalToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertDetailContract(readObject(detailResult));
    }

    private void assertSummaryContract(Map<String, Object> summary) {
        assertThat(summary.keySet()).containsExactlyInAnyOrderElementsOf(SUMMARY_FIELDS);
        assertThat(summary.keySet())
                .doesNotContain("primaryGoal")
                .doesNotContainAnyElementsOf(EXCLUDED_PROFESSIONAL_FIELDS);
        assertThat(summary).containsKey("profileImageUrl");
        assertThat(summary.get("profileImageUrl")).isNull();
    }

    private void assertDetailContract(Map<String, Object> detail) {
        assertThat(detail.keySet()).containsExactlyInAnyOrderElementsOf(DETAIL_FIELDS);
        assertThat(detail.keySet()).doesNotContainAnyElementsOf(EXCLUDED_PROFESSIONAL_FIELDS);
        assertThat(detail).containsKey("profileImageUrl");
        assertThat(detail.get("profileImageUrl")).isNull();
    }

    private void assertUpdatedOwnerProfile(Map<String, Object> profile) {
        assertThat(profile.get("heightCm")).isEqualTo(175.50);
        assertThat(profile.get("medicalNotes")).isEqualTo("Controllo aggiornato");
        assertThat(profile).containsKey("injuryNotes");
        assertThat(profile.get("injuryNotes")).isNull();
        assertThat(profile.get("notes")).isEqualTo("Preferisce sessioni mattutine");
        assertThat(profile.get("birthDate")).isEqualTo("1996-04-15");
        assertThat(profile.get("primaryGoal")).isEqualTo("Migliorare la forma fisica");
        assertThat(profile.get("gender")).isEqualTo("MALE");
        assertThat(profile.get("operationalStatus")).isEqualTo("ATTIVO");
    }

    private String registerVerifyAndLoginProfessional(
            String email,
            ProfessionalSpecialization specialization
    ) throws Exception {
        String registrationRequestBody = """
                {
                  "firstName": "Mario",
                  "lastName": "Rossi",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "%s"
                }
                """.formatted(email, PASSWORD, specialization.name());

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

        return login(email);
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
                  "medicalNotes": "Controllo medico periodico",
                  "injuryNotes": "Distorsione caviglia pregressa",
                  "notes": "Preferisce sessioni mattutine",
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

        return login(email);
    }

    private void confirmEmail(String token) throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk());
    }

    private String login(String email) throws Exception {
        String loginRequestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private Map<String, Object> readObject(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
