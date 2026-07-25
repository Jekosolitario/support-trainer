package it.zuperman.support_trainer.email;

import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.auth.token.EmailVerificationToken;
import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.MutableTestClock;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

import static it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.email.mode=SMTP",
        "app.email.verification-page-url=https://frontend.test/verify-email",
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
class EmailVerificationSenderFailureIntegrationTest {

    private static final String EMAIL = "sender.failure@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MutableTestClock clock;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailVerificationTokenRepository tokenRepository;
    @Autowired
    private InviteCodeRepository inviteRepository;
    @Autowired
    private ProfessionalClientLinkRepository linkRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        clock.setInstant(INITIAL_INSTANT);
        when(mailSender.createMimeMessage()).thenAnswer(invocation ->
                new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
        doThrow(new MailSendException("delivery unavailable")).when(mailSender).send(any(jakarta.mail.internet.MimeMessage.class));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void senderFailureShouldNotChangeRegistrationOrResendResponses(CapturedOutput output) throws Exception {
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register/professional")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Mario",
                                  "lastName":"Rossi",
                                  "email":"%s",
                                  "password":"%s",
                                  "specialization":"PERSONAL_TRAINER"
                                }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isAccepted())
                .andReturn();

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        List<EmailVerificationToken> registrationTokens = tokensFor(user);
        verify(mailSender, times(1)).send(any(jakarta.mail.internet.MimeMessage.class));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailVerified()).isFalse();
        assertThat(registrationTokens).hasSize(1);
        assertThat(registration.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(registrationTokens.get(0).getToken());
        assertThat(output.getAll())
                .contains("REGISTRATION", "EmailDeliveryException")
                .doesNotContain(EMAIL, registrationTokens.get(0).getToken(), "https://frontend.test/verify-email");

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(EMAIL)))
                .andExpect(status().isAccepted());

        verify(mailSender, times(2)).send(any(jakarta.mail.internet.MimeMessage.class));
        assertThat(tokensFor(user)).hasSize(2);
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    private List<EmailVerificationToken> tokensFor(User user) {
        return tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(user.getId());
    }

    private void cleanDatabase() {
        tokenRepository.deleteAll();
        linkRepository.deleteAll();
        inviteRepository.deleteAll();
        userRepository.deleteAll();
    }
}
