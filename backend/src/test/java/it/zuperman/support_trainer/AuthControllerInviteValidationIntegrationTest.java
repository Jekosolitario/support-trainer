package it.zuperman.support_trainer;

import java.time.Instant;

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

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.invite.entity.InviteCode;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
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
class AuthControllerInviteValidationIntegrationTest {

    private static final String VALIDATE_INVITE_ENDPOINT
            = "/api/v1/auth/register/client/validate-invite";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    @Test
    @DisplayName("Deve validare un codice invito valido senza consumarlo")
    void shouldValidateActiveUnusedInviteCode() throws Exception {
        String inviteCodeValue = "VALID-INVITE-CODE";
        createValidInvite(
                inviteCodeValue,
                "professional.valid.invite.validation@example.com"
        );

        String requestBody = """
                {
                  "code": "%s"
                }
                """.formatted(inviteCodeValue);

        validateInvite(requestBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.code").value(inviteCodeValue));

        InviteCode validatedInvite = inviteCodeRepository.findByCode(inviteCodeValue).orElseThrow();
        assertThat(validatedInvite.getUsed()).isFalse();
        assertThat(validatedInvite.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("Deve restituire not found per un codice invito inesistente")
    void shouldRejectMissingInviteCode() throws Exception {
        String requestBody = """
                {
                  "code": "MISSING-INVITE-CODE"
                }
                """;

        validateInvite(requestBody)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Deve rifiutare un codice invito già usato")
    void shouldRejectUsedInviteCode() throws Exception {
        String inviteCodeValue = "USED-INVITE-CODE";
        InviteCode inviteCode = createValidInvite(
                inviteCodeValue,
                "professional.used.invite.validation@example.com"
        );
        inviteCode.setUsed(true);
        inviteCode.setUsedAt(Instant.now().minusSeconds(3_600));
        inviteCodeRepository.saveAndFlush(inviteCode);

        String requestBody = """
                {
                  "code": "%s"
                }
                """.formatted(inviteCodeValue);

        validateInvite(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_ALREADY_USED"));
    }

    @Test
    @DisplayName("Deve rifiutare un codice invito scaduto")
    void shouldRejectExpiredInviteCode() throws Exception {
        String inviteCodeValue = "EXPIRED-INVITE-CODE";
        InviteCode inviteCode = createValidInvite(
                inviteCodeValue,
                "professional.expired.invite.validation@example.com"
        );
        inviteCode.setExpiresAt(Instant.now().minusSeconds(86_400));
        inviteCodeRepository.saveAndFlush(inviteCode);

        String requestBody = """
                {
                  "code": "%s"
                }
                """.formatted(inviteCodeValue);

        validateInvite(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_EXPIRED"));
    }

    @Test
    @DisplayName("Deve rifiutare un codice invito inattivo")
    void shouldRejectInactiveInviteCode() throws Exception {
        String inviteCodeValue = "INACTIVE-INVITE-CODE";
        InviteCode inviteCode = createValidInvite(
                inviteCodeValue,
                "professional.inactive.invite.validation@example.com"
        );
        inviteCode.setActive(false);
        inviteCodeRepository.saveAndFlush(inviteCode);

        String requestBody = """
                {
                  "code": "%s"
                }
                """.formatted(inviteCodeValue);

        validateInvite(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("Deve rifiutare l'invito di un professionista inattivo")
    void shouldRejectInviteOwnedByInactiveProfessional() throws Exception {
        String inviteCodeValue = "INACTIVE-PROFESSIONAL-INVITE";
        InviteCode inviteCode = createValidInvite(
                inviteCodeValue,
                "inactive.professional.invite.validation@example.com"
        );
        ProfessionalProfile professional = inviteCode.getProfessional();
        professional.setActive(false);
        professionalProfileRepository.saveAndFlush(professional);

        String requestBody = """
                {
                  "code": "%s"
                }
                """.formatted(inviteCodeValue);

        validateInvite(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("Deve rifiutare l'invito di un professionista non verificato")
    void shouldRejectInviteOwnedByUnverifiedProfessional() throws Exception {
        String inviteCodeValue = "UNVERIFIED-PROFESSIONAL-INVITE";
        InviteCode inviteCode = createValidInvite(
                inviteCodeValue,
                "unverified.professional.invite.validation@example.com"
        );
        ProfessionalProfile professional = inviteCode.getProfessional();
        professional.setEmailVerified(false);
        professionalProfileRepository.saveAndFlush(professional);

        String requestBody = """
                {
                  "code": "%s"
                }
                """.formatted(inviteCodeValue);

        validateInvite(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("Deve rifiutare l'invito di un professionista con account non attivo")
    void shouldRejectInviteOwnedByProfessionalWithInactiveAccount() throws Exception {
        String inviteCodeValue = "INACTIVE-ACCOUNT-PROFESSIONAL-INVITE";
        InviteCode inviteCode = createValidInvite(
                inviteCodeValue,
                "inactive.account.professional.invite.validation@example.com"
        );
        ProfessionalProfile professional = inviteCode.getProfessional();
        professional.setAccountStatus(AccountStatus.PENDING_VERIFICATION);
        professionalProfileRepository.saveAndFlush(professional);

        String requestBody = """
                {
                  "code": "%s"
                }
                """.formatted(inviteCodeValue);

        validateInvite(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITE_CODE_NOT_ACTIVE"));
    }

    private InviteCode createValidInvite(String code, String professionalEmail) {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Mario",
                "Rossi",
                professionalEmail,
                "encoded-password",
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setActive(true);
        professional.setEmailVerified(true);
        professional.setAccountStatus(AccountStatus.ACTIVE);
        ProfessionalProfile savedProfessional = professionalProfileRepository.saveAndFlush(professional);

        InviteCode inviteCode = new InviteCode(
                code,
                savedProfessional,
                Instant.now().plusSeconds(86_400)
        );

        return inviteCodeRepository.saveAndFlush(inviteCode);
    }

    private org.springframework.test.web.servlet.ResultActions validateInvite(String requestBody) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        return mockMvc.perform(post(VALIDATE_INVITE_ENDPOINT)
                .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));
    }
}
