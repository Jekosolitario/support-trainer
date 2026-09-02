package it.zuperman.support_trainer.email;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.extension.ExtendWith;

import it.zuperman.support_trainer.auth.repository.PasswordResetTokenRepository;
import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.ProfessionalSpecialization;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.email.config.PasswordRecoveryDeliveryExecutorConfiguration;
import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;
import it.zuperman.support_trainer.email.port.PasswordRecoverySender;
import it.zuperman.support_trainer.professional.entity.ProfessionalProfile;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.support.SessionAuthTestSupport;
import it.zuperman.support_trainer.support.SessionAuthTestSupport.CsrfSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:password_recovery_async_delivery;MODE=MySQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryAsyncDeliveryIntegrationTest {

    private static final String EMAIL = "recovery.async.delivery@example.com";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private ProfessionalProfileRepository professionalProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    @Qualifier(PasswordRecoveryDeliveryExecutorConfiguration.EXECUTOR_BEAN_NAME)
    private ThreadPoolTaskExecutor passwordRecoveryDeliveryExecutor;

    @MockitoBean
    private PasswordRecoverySender sender;

    private final CountDownLatch senderStarted = new CountDownLatch(1);
    private final CountDownLatch allowSenderToComplete = new CountDownLatch(1);
    private final CountDownLatch senderFinished = new CountDownLatch(1);
    private final AtomicInteger sendCount = new AtomicInteger();

    @BeforeEach
    void setUp() {
        cleanDatabase();
        doAnswer(invocation -> {
            sendCount.incrementAndGet();
            senderStarted.countDown();
            if (!allowSenderToComplete.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("blocked sender was not released");
            }
            senderFinished.countDown();
            return null;
        }).when(sender).send(any(PasswordRecoveryMessage.class));
    }

    @AfterEach
    void tearDown() {
        allowSenderToComplete.countDown();
        cleanDatabase();
    }

    @Test
    @DisplayName("POST request completes while the sender is still blocked, then a single worker send finishes")
    void eligibleRequestMustNotWaitForBlockedSender(CapturedOutput output) throws Exception {
        ProfessionalProfile professional = new ProfessionalProfile(
                "Async",
                "Delivery",
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                ProfessionalSpecialization.PERSONAL_TRAINER
        );
        professional.setAccountStatus(AccountStatus.ACTIVE);
        professional.setEmailVerified(true);
        professionalProfileRepository.saveAndFlush(professional);

        long completedBefore = passwordRecoveryDeliveryExecutor.getThreadPoolExecutor().getCompletedTaskCount();
        CsrfSession csrf = SessionAuthTestSupport.fetchCsrf(mockMvc);

        try (ExecutorService httpExecutor = Executors.newSingleThreadExecutor()) {
            Future<MvcResult> request = httpExecutor.submit(() -> mockMvc.perform(
                            post("/api/v1/auth/password-recovery/request")
                                    .with(SessionAuthTestSupport.withSessionAndCsrf(csrf))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"email\":\"%s\"}".formatted(EMAIL)))
                    .andReturn());

            MvcResult response = request.get(3, TimeUnit.SECONDS);
            assertThat(response.getResponse().getStatus()).isEqualTo(202);
            assertThat(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .doesNotContain("token")
                    .doesNotContain(EMAIL);
            assertThat(allowSenderToComplete.getCount()).isEqualTo(1);
            assertThat(tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(professional.getId())).hasSize(1);
            assertThat(tokenRepository.findByUser_IdOrderByCreatedAtDescIdDesc(professional.getId()).get(0).getConsumedAt())
                    .isNull();
        }

        assertThat(senderStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(allowSenderToComplete.getCount()).isEqualTo(1);
        verify(sender, times(1)).send(any(PasswordRecoveryMessage.class));

        allowSenderToComplete.countDown();
        assertThat(senderFinished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(sendCount.get()).isEqualTo(1);
        long completionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (passwordRecoveryDeliveryExecutor.getThreadPoolExecutor().getCompletedTaskCount() < completedBefore + 1) {
            if (System.nanoTime() >= completionDeadline) {
                throw new AssertionError("password recovery executor did not complete the single delivery task");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
        verify(sender, times(1)).send(any(PasswordRecoveryMessage.class));
        assertThat(output.getAll()).doesNotContain(EMAIL, "Password123!", "#token=");
    }

    private void cleanDatabase() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }
}
