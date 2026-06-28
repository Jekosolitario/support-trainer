package it.zuperman.support_trainer;

import java.time.LocalDateTime;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
