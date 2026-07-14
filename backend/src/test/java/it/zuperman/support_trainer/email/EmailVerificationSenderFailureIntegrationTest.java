package it.zuperman.support_trainer.email;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
import it.zuperman.support_trainer.email.model.EmailVerificationMessage;
import it.zuperman.support_trainer.email.port.EmailVerificationSender;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration;
import it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.MutableTestClock;
import it.zuperman.support_trainer.invite.repository.InviteCodeRepository;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;

import static it.zuperman.support_trainer.email.support.EmailTestClockConfiguration.INITIAL_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
    private EmailVerificationSender sender;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        clock.setInstant(INITIAL_INSTANT);
        doThrow(new IllegalStateException("delivery unavailable")).when(sender).send(any());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void senderFailureShouldNotChangeRegistrationOrResendResponses(CapturedOutput output) throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register/professional")
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
                .andExpect(status().isCreated())
                .andReturn();

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        List<EmailVerificationToken> registrationTokens = tokensFor(user);
        ArgumentCaptor<EmailVerificationMessage> captor = ArgumentCaptor.forClass(EmailVerificationMessage.class);
        verify(sender, times(1)).send(captor.capture());

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.getEmailVerified()).isFalse();
        assertThat(registrationTokens).hasSize(1);
        assertThat(registration.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(registrationTokens.get(0).getToken());
        assertThat(output.getAll())
                .contains(captor.getValue().correlationId().toString(), "REGISTRATION", "IllegalStateException")
                .doesNotContain(EMAIL, registrationTokens.get(0).getToken(), captor.getValue().verificationUrl());

        clock.setInstant(INITIAL_INSTANT.plusSeconds(60));
        mockMvc.perform(post("/api/v1/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(EMAIL)))
                .andExpect(status().isAccepted());

        verify(sender, times(2)).send(any());
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
