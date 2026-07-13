package it.zuperman.support_trainer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Transactional
class AuthControllerRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private ProfessionalClientLinkRepository professionalClientLinkRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Test
    @DisplayName("Deve accettare in registrazione una password di esattamente 72 byte UTF-8")
    void shouldAcceptProfessionalRegistrationWithExactlySeventyTwoUtf8Bytes() throws Exception {
        String email = "password.exact.limit@example.com";
        String password = "A1!" + "a".repeat(69);
        assertThat(password).hasSize(72);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);

        String requestBody = """
                {
                  "firstName": "Limite",
                  "lastName": "Esatto",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        assertThat(professionalProfileRepository.findByEmail(email)).isPresent();
    }

    @Test
    @DisplayName("Deve rifiutare in registrazione una password ASCII di 73 byte")
    void shouldRejectProfessionalRegistrationWithSeventyThreeAsciiBytes() throws Exception {
        String email = "password.ascii.over.limit@example.com";
        String password = "A1!" + "a".repeat(70);
        assertThat(password).hasSize(73);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);

        String requestBody = """
                {
                  "firstName": "Limite",
                  "lastName": "Superato",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Dati non validi"))
                .andExpect(jsonPath("$.validationErrors.password")
                        .value("La password non può superare 72 byte in codifica UTF-8"));

        assertThat(professionalProfileRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("Deve contare i byte UTF-8 nelle password Unicode di registrazione")
    void shouldRejectClientRegistrationWithUnicodePasswordOverSeventyTwoUtf8Bytes() throws Exception {
        String email = "password.unicode.over.limit@example.com";
        String password = "A1!" + "€".repeat(24);
        assertThat(password).hasSize(27);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(75);

        String requestBody = """
                {
                  "firstName": "Unicode",
                  "lastName": "Limite",
                  "email": "%s",
                  "password": "%s",
                  "birthDate": "1995-01-01",
                  "heightCm": 170.00,
                  "primaryGoal": "Migliorare la forma fisica",
                  "gender": "FEMALE",
                  "inviteCode": "INVITE-NOT-USED"
                }
                """.formatted(email, password);

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors.password")
                        .value("La password non può superare 72 byte in codifica UTF-8"));

        assertThat(clientProfileRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("Deve preservare il requisito minimo di 8 caratteri in registrazione")
    void shouldRejectProfessionalRegistrationBelowExistingMinimumLength() throws Exception {
        String email = "password.below.minimum@example.com";
        String requestBody = """
                {
                  "firstName": "Minimo",
                  "lastName": "Invariato",
                  "email": "%s",
                  "password": "A1!aaaa",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors.password")
                        .value("La password deve contenere almeno 8 caratteri"));

        assertThat(professionalProfileRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("Non deve registrare due professionisti con la stessa email")
    void shouldRejectProfessionalRegistrationWithDuplicateEmail() throws Exception {
        String requestBody = """
                {
                  "firstName": "Marco",
                  "lastName": "Ferrari",
                  "email": "duplicate.professional@example.com",
                  "password": "Password123!",
                  "specialization": "PERSONAL_TRAINER"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("Non deve registrare un cliente con codice invito inesistente")
    void shouldRejectClientRegistrationWithMissingInviteCode() throws Exception {
        String requestBody = """
                {
                  "firstName": "Laura",
                  "lastName": "Conti",
                  "email": "laura.conti.invite.notfound@example.com",
                  "password": "Password123!",
                  "birthDate": "1995-01-01",
                  "heightCm": 170.00,
                  "primaryGoal": "Migliorare la forma fisica",
                  "gender": "FEMALE",
                  "inviteCode": "INVITE-NOT-EXISTING"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INVITE_CODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Deve registrare un cliente con codice invito valido")
    void shouldRegisterClientWithValidInviteCode() throws Exception {
        String clientEmail = "elena.ricci.valid.invite@example.com";
        String inviteCodeValue = "VALID-CLIENT-INVITE";

        ProfessionalProfile professional = new ProfessionalProfile(
                "Andrea",
                "Moretti",
                "andrea.moretti.valid.invite@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);
        ProfessionalProfile savedProfessional = professionalProfileRepository.saveAndFlush(professional);

        InviteCode inviteCode = new InviteCode(
                inviteCodeValue,
                savedProfessional,
                LocalDateTime.now().plusDays(1)
        );
        inviteCodeRepository.saveAndFlush(inviteCode);

        String requestBody = """
                {
                  "firstName": "Elena",
                  "lastName": "Ricci",
                  "email": "%s",
                  "password": "Password123!",
                  "birthDate": "1994-05-12",
                  "heightCm": 168.00,
                  "primaryGoal": "Aumentare la forza",
                  "gender": "FEMALE",
                  "inviteCode": "%s"
                }
                """.formatted(clientEmail, inviteCodeValue);

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(clientEmail))
                .andExpect(jsonPath("$.role").value("CLIENT"));

        ClientProfile savedClient = clientProfileRepository.findByEmail(clientEmail).orElseThrow();
        assertThat(savedClient.getEmail()).isEqualTo(clientEmail);
        assertThat(savedClient.getFirstName()).isEqualTo("Elena");
        assertThat(savedClient.getLastName()).isEqualTo("Ricci");
        assertThat(savedClient.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savedClient.getEmailVerified()).isTrue();

        assertThat(professionalClientLinkRepository
                .existsByProfessional_IdAndClient_IdAndActiveTrue(
                        savedProfessional.getId(),
                        savedClient.getId()
                ))
                .isTrue();

        InviteCode usedInviteCode = inviteCodeRepository.findByCode(inviteCodeValue).orElseThrow();
        assertThat(usedInviteCode.getUsed()).isTrue();
        assertThat(usedInviteCode.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Non deve registrare un secondo cliente con un invito già usato")
    void shouldRejectSecondClientRegistrationWithUsedInviteCode() throws Exception {
        String professionalEmail = "professional.used.invite@example.com";
        String password = "Password123!";
        String professionalRegistrationRequestBody = """
                {
                  "firstName": "Matteo",
                  "lastName": "Riva",
                  "email": "%s",
                  "password": "%s",
                  "specialization": "PERSONAL_TRAINER"
                }
                """.formatted(professionalEmail, password);

        mockMvc.perform(post("/api/v1/auth/register/professional")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(professionalRegistrationRequestBody))
                .andExpect(status().isCreated());

        ProfessionalProfile savedProfessional = professionalProfileRepository
                .findByEmail(professionalEmail)
                .orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedProfessional.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/auth/verify-email")
                        .param("token", verificationToken.getToken()))
                .andExpect(status().isOk());

        String professionalLoginRequestBody = """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(professionalEmail, password);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(professionalLoginRequestBody))
                .andExpect(status().isOk())
                .andReturn();
        String professionalToken = JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.accessToken"
        );

        MvcResult inviteResult = mockMvc.perform(post("/api/v1/invites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + professionalToken))
                .andExpect(status().isCreated())
                .andReturn();
        String inviteCode = JsonPath.read(
                inviteResult.getResponse().getContentAsString(),
                "$.code"
        );

        String firstClientRequestBody = """
                {
                  "firstName": "Alice",
                  "lastName": "Fontana",
                  "email": "alice.fontana.used.invite@example.com",
                  "password": "Password123!",
                  "birthDate": "1995-03-20",
                  "heightCm": 165.00,
                  "primaryGoal": "Migliorare la forma fisica",
                  "gender": "FEMALE",
                  "inviteCode": "%s"
                }
                """.formatted(inviteCode);

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstClientRequestBody))
                .andExpect(status().isCreated());

        String secondClientRequestBody = """
                {
                  "firstName": "Davide",
                  "lastName": "Greco",
                  "email": "davide.greco.used.invite@example.com",
                  "password": "Password123!",
                  "birthDate": "1993-09-12",
                  "heightCm": 180.00,
                  "primaryGoal": "Aumentare la forza",
                  "gender": "MALE",
                  "inviteCode": "%s"
                }
                """.formatted(inviteCode);

        mockMvc.perform(post("/api/v1/auth/register/client")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondClientRequestBody))
                .andExpect(status().isBadRequest());
    }
}
