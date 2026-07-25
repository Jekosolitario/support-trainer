package it.zuperman.support_trainer;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.client.entity.ClientProfile;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Gender;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;
import jakarta.transaction.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

    @Autowired
    private ApplicationTimeProvider timeProvider;

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

        registerProfessionalRequest(requestBody)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty());

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

        registerProfessionalRequest(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La richiesta contiene dati non validi"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
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

        registerClientRequest(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
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

        registerProfessionalRequest(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("La password deve contenere almeno 8 caratteri"));

        assertThat(professionalProfileRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("La registrazione Professional deve essere neutra per email già esistenti")
    void shouldReturnSameNeutralProfessionalRegistrationResponseForDuplicateEmail() throws Exception {
        String requestBody = """
                {
                  "firstName": "Marco",
                  "lastName": "Ferrari",
                  "email": "duplicate.professional@example.com",
                  "password": "Password123!",
                  "specialization": "PERSONAL_TRAINER"
                }
                """;

        MvcResult firstRegistration = registerProfessionalRequest(requestBody)
                .andExpect(status().isAccepted())
                .andReturn();

        long tokenCountAfterFirstRegistration = emailVerificationTokenRepository.count();
        long professionalCountAfterFirstRegistration = professionalProfileRepository.count();

        MvcResult secondRegistration = registerProfessionalRequest(requestBody)
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(secondRegistration.getResponse().getContentAsString())
                .isEqualTo(firstRegistration.getResponse().getContentAsString());
        assertThat(secondRegistration.getResponse().getContentAsString())
                .isEqualTo("{\"message\":\"Se la registrazione può essere completata, riceverai le istruzioni per verificare l'indirizzo email\"}");
        assertThat(professionalProfileRepository.count()).isEqualTo(professionalCountAfterFirstRegistration);
        assertThat(emailVerificationTokenRepository.count()).isEqualTo(tokenCountAfterFirstRegistration);
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

        registerClientRequest(requestBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Deve registrare un cliente con codice invito valido")
    void shouldRegisterClientWithValidInviteCode() throws Exception {
        String clientEmail = "elena.ricci.valid.invite@example.com";
        String inviteCodeValue = "VALID-CLIENT-INVITE";
        Instant beforeRegistration = timeProvider.nowInstant();

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
                Instant.now().plusSeconds(86_400)
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

        registerClientRequest(requestBody)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty());

        Instant afterRegistration = timeProvider.nowInstant();

        ClientProfile savedClient = clientProfileRepository.findByEmail(clientEmail).orElseThrow();
        assertThat(savedClient.getEmail()).isEqualTo(clientEmail);
        assertThat(savedClient.getFirstName()).isEqualTo("Elena");
        assertThat(savedClient.getLastName()).isEqualTo("Ricci");
        assertThat(savedClient.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(savedClient.getEmailVerified()).isFalse();
        assertThat(savedClient.getActive()).isTrue();

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedClient.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(verificationToken.getUsed()).isFalse();
        assertThat(verificationToken.getExpiresAt())
                .isAfterOrEqualTo(beforeRegistration.plusSeconds(86_400))
                .isBeforeOrEqualTo(afterRegistration.plusSeconds(86_400));

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
    @DisplayName("La registrazione Client non deve consumare l'invito per email già esistente")
    void shouldKeepInviteUntouchedForExistingClientEmail() throws Exception {
        String existingEmail = "existing.client.neutral@example.com";
        ClientProfile existingClient = new ClientProfile(
                "Giulia",
                "Neri",
                existingEmail,
                "encoded-password",
                LocalDate.of(1995, 1, 1),
                BigDecimal.valueOf(170),
                "Migliorare la forma fisica",
                Gender.FEMALE
        );
        existingClient.setAccountStatus(AccountStatus.ACTIVE);
        existingClient.setEmailVerified(true);
        existingClient.setActive(true);
        clientProfileRepository.saveAndFlush(existingClient);

        ProfessionalProfile professional = new ProfessionalProfile(
                "Andrea",
                "Moretti",
                "professional.neutral.client@example.com",
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professional.setActive(true);
        professional = professionalProfileRepository.saveAndFlush(professional);

        InviteCode inviteCode = inviteCodeRepository.saveAndFlush(new InviteCode(
                "VALID-NEUTRAL-CLIENT-INVITE",
                professional,
                Instant.now().plusSeconds(86_400)
        ));
        long tokenCountBeforeRequest = emailVerificationTokenRepository.count();
        long linkCountBeforeRequest = professionalClientLinkRepository.count();

        String requestBody = """
                {
                  "firstName": "Giulia",
                  "lastName": "Neri",
                  "email": "%s",
                  "password": "Password123!",
                  "birthDate": "1995-01-01",
                  "heightCm": 170.00,
                  "primaryGoal": "Migliorare la forma fisica",
                  "gender": "FEMALE",
                  "inviteCode": "%s"
                }
                """.formatted(existingEmail, inviteCode.getCode());

        MvcResult response = registerClientRequest(requestBody)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn();

        assertThat(response.getResponse().getContentAsString())
                .isEqualTo("{\"message\":\"Se la registrazione può essere completata, riceverai le istruzioni per verificare l'indirizzo email\"}");
        InviteCode unchangedInvite = inviteCodeRepository.findByCode(inviteCode.getCode()).orElseThrow();
        assertThat(unchangedInvite.getUsed()).isFalse();
        assertThat(unchangedInvite.getUsedAt()).isNull();
        assertThat(professionalClientLinkRepository.count()).isEqualTo(linkCountBeforeRequest);
        assertThat(emailVerificationTokenRepository.count()).isEqualTo(tokenCountBeforeRequest);
        assertThat(clientProfileRepository.findByEmail(existingEmail)).isPresent();
    }

    @Test
    @DisplayName("La registrazione Client deve validare l'invito prima di neutralizzare un'email esistente")
    void shouldRejectInvalidInviteBeforeCheckingExistingClientEmail() throws Exception {
        String existingEmail = "existing.client.invalid.invite@example.com";
        ClientProfile existingClient = new ClientProfile(
                "Giulia",
                "Neri",
                existingEmail,
                "encoded-password",
                LocalDate.of(1995, 1, 1),
                BigDecimal.valueOf(170),
                "Migliorare la forma fisica",
                Gender.FEMALE
        );
        existingClient.setAccountStatus(AccountStatus.ACTIVE);
        existingClient.setEmailVerified(true);
        existingClient.setActive(true);
        clientProfileRepository.saveAndFlush(existingClient);

        registerClientRequest("""
                                {
                                  "firstName": "Giulia",
                                  "lastName": "Neri",
                                  "email": "%s",
                                  "password": "Password123!",
                                  "birthDate": "1995-01-01",
                                  "heightCm": 170.00,
                                  "primaryGoal": "Migliorare la forma fisica",
                                  "gender": "FEMALE",
                                  "inviteCode": "INVITE-NOT-EXISTING"
                                }
                                """.formatted(existingEmail))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));
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

        registerProfessionalRequest(professionalRegistrationRequestBody)
                .andExpect(status().isAccepted());

        ProfessionalProfile savedProfessional = professionalProfileRepository
                .findByEmail(professionalEmail)
                .orElseThrow();
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(savedProfessional.getId()))
                .findFirst()
                .orElseThrow();

        confirmEmail(verificationToken.getToken());

        CsrfSession professionalAuth = SessionAuthTestSupport.loginAndRefreshCsrf(
                mockMvc,
                professionalEmail,
                password
        );

        MvcResult inviteResult = mockMvc.perform(post("/api/v1/invites")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(professionalAuth)))
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

        registerClientRequest(firstClientRequestBody)
                .andExpect(status().isAccepted());

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

        registerClientRequest(secondClientRequestBody)
                .andExpect(status().isBadRequest());
    }

    private ResultActions registerProfessionalRequest(String content) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        return mockMvc.perform(post("/api/v1/auth/register/professional")
                .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
    }

    private ResultActions registerClientRequest(String content) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        return mockMvc.perform(post("/api/v1/auth/register/client")
                .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .content(content));
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
