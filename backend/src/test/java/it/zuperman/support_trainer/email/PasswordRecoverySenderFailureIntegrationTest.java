package it.zuperman.support_trainer.email;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import it.zuperman.support_trainer.auth.repository.PasswordResetTokenRepository;
import it.zuperman.support_trainer.auth.token.PasswordResetToken;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.MutableTestClock;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

import static it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.email.mode=SMTP",
        "app.email.verification-page-url=https://frontend.test/verify-email",
        "app.email.password-recovery-page-url=https://frontend.test/reset-password",
        "app.email.sender.address=no-reply@example.test",
        "app.email.sender.name=Support Trainer",
        "app.email.smtp.host=smtp.example.test",
        "app.email.smtp.port=2525",
        "app.email.smtp.auth=false",
        "app.email.smtp.start-tls=false",
        "app.email.smtp.connect-timeout=5s",
        "app.email.smtp.read-timeout=5s",
        "app.email.smtp.write-timeout=5s"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(EmailTestClockConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoverySenderFailureIntegrationTest {

    private static final String EMAIL = "recovery.sender.failure@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MutableTestClock clock;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        clock.setInstant(INITIAL_INSTANT);
        when(mailSender.createMimeMessage()).thenAnswer(invocation ->
                new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
        doThrow(new MailSendException("delivery unavailable")).when(mailSender)
                .send(any(jakarta.mail.internet.MimeMessage.class));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void senderFailureMustNotRollbackCommittedRecoveryRequest(CapturedOutput output) throws Exception {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Sender",
                "Failure",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professionalProfileRepository.saveAndFlush(professional);

        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        MvcResult response = mockMvc.perform(post("/api/v1/auth/password-recovery/request")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(EMAIL)))
                .andExpect(status().isAccepted())
                .andReturn();

        verify(mailSender, timeout(5_000).times(1)).send(any(jakarta.mail.internet.MimeMessage.class));
        PasswordResetToken stored = tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(professional.getId()).get(0);
        assertThat(stored.getConsumedAt()).isNull();
        assertThat(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(stored.getTokenHash());
        assertThat(output.getAll())
                .contains("Password recovery delivery failed", "PasswordRecoveryDeliveryException")
                .doesNotContain(EMAIL, "NewPass123!", stored.getTokenHash());
    }

    private void cleanDatabase() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
