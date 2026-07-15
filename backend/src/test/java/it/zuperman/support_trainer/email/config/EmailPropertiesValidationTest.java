package it.zuperman.support_trainer.email.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.email.adapter.DisabledEmailVerificationSender;
import it.zuperman.support_trainer.email.adapter.InMemoryEmailVerificationSender;
import it.zuperman.support_trainer.email.adapter.SmtpEmailVerificationSender;
import it.zuperman.support_trainer.email.port.EmailVerificationSender;
import it.zuperman.support_trainer.email.service.EmailVerificationEmailComposer;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPropertiesValidationTest {

    private static final String MODE = "app.email.mode";
    private static final String VERIFICATION_PAGE_URL = "app.email.verification-page-url";
    private static final String VALID_HTTPS_URL = "https://frontend.example/verify-email";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldCreateOnlyDisabledSenderWithoutSmtpConfiguration() {
        contextRunner.withPropertyValues(MODE + "=DISABLED", VERIFICATION_PAGE_URL + "=" + VALID_HTTPS_URL)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(EmailVerificationSender.class);
                    assertThat(context.getBean(EmailVerificationSender.class))
                            .isInstanceOf(DisabledEmailVerificationSender.class);
                });
    }

    @Test
    void shouldCreateOnlyInMemorySenderWithoutSmtpConfiguration() {
        contextRunner.withPropertyValues(MODE + "=IN_MEMORY", VERIFICATION_PAGE_URL + "=" + VALID_HTTPS_URL)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(EmailVerificationSender.class);
                    assertThat(context.getBean(EmailVerificationSender.class))
                            .isInstanceOf(InMemoryEmailVerificationSender.class);
                });
    }

    @Test
    void shouldCreateOnlySmtpSenderWithCompleteSmtpConfiguration() {
        smtpContext().run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(EmailVerificationSender.class);
            assertThat(context).hasSingleBean(JavaMailSender.class);
            assertThat(context.getBean(EmailVerificationSender.class))
                    .isInstanceOf(SmtpEmailVerificationSender.class);
        });
    }

    @Test
    void shouldDefaultToDisabledModeWhenModeIsMissing() {
        contextRunner.withPropertyValues(VERIFICATION_PAGE_URL + "=" + VALID_HTTPS_URL)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(EmailProperties.class).mode()).isEqualTo(EmailMode.DISABLED);
                    assertThat(context.getBean(EmailVerificationSender.class))
                            .isInstanceOf(DisabledEmailVerificationSender.class);
                });
    }

    @Test
    void shouldRejectUnsupportedMode() {
        assertConfigurationFails(MODE, MODE + "=UNSUPPORTED", VERIFICATION_PAGE_URL + "=" + VALID_HTTPS_URL);
    }

    @Test
    void shouldRejectMissingVerificationPageUrl() {
        assertConfigurationFails(VERIFICATION_PAGE_URL, MODE + "=DISABLED");
    }

    @Test
    void shouldRejectRelativeVerificationPageUrl() {
        assertInvalidUrl("verify-email");
    }

    @Test
    void shouldRejectNonHttpVerificationPageUrl() {
        assertInvalidUrl("ftp://frontend.example/verify-email");
    }

    @Test
    void shouldRejectVerificationPageUrlWithQuery() {
        assertInvalidUrl("https://frontend.example/verify-email?source=test");
    }

    @Test
    void shouldRejectVerificationPageUrlWithFragment() {
        assertInvalidUrl("https://frontend.example/verify-email#old");
    }

    @Test
    void shouldRejectRemoteHttpUrlWhenDeliveryIsEnabled() {
        assertConfigurationFails(
                VERIFICATION_PAGE_URL,
                MODE + "=IN_MEMORY",
                VERIFICATION_PAGE_URL + "=http://frontend.example/verify-email"
        );
    }

    @Test
    void shouldAcceptLocalhostHttpUrlWhenDeliveryIsEnabled() {
        contextRunner.withPropertyValues(
                        MODE + "=IN_MEMORY",
                        VERIFICATION_PAGE_URL + "=http://localhost:5173/verify-email"
                )
                .run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    @Test
    void shouldAcceptAbsoluteHttpsUrlWithBasePath() {
        contextRunner.withPropertyValues(
                        MODE + "=IN_MEMORY",
                        VERIFICATION_PAGE_URL + "=https://frontend.example/app/verify-email"
                )
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(EmailProperties.class).verificationPageUrl().toString())
                            .isEqualTo("https://frontend.example/app/verify-email");
                });
    }

    @Test
    void shouldRejectSmtpWithoutSenderAddress() {
        assertSmtpFails("app.email.sender.address", "app.email.sender.address=");
    }

    @Test
    void shouldRejectSmtpWithInvalidSenderAddress() {
        assertSmtpFails("app.email.sender.address", "app.email.sender.address=not-an-email");
    }

    @Test
    void shouldRejectSmtpWithoutSenderName() {
        assertSmtpFails("app.email.sender.name", "app.email.sender.name= ");
    }

    @Test
    void shouldRejectSmtpWithInvalidReplyTo() {
        assertSmtpFails("app.email.sender.reply-to", "app.email.sender.reply-to=not-an-email");
    }

    @Test
    void shouldRejectSmtpWithoutHost() {
        assertSmtpFails("app.email.smtp.host", "app.email.smtp.host=");
    }

    @Test
    void shouldRejectSmtpPortZero() {
        assertSmtpFails("app.email.smtp.port", "app.email.smtp.port=0");
    }

    @Test
    void shouldRejectSmtpPortAboveRange() {
        assertSmtpFails("app.email.smtp.port", "app.email.smtp.port=65536");
    }

    @Test
    void shouldRejectZeroTimeout() {
        assertSmtpFails("app.email.smtp.connect-timeout", "app.email.smtp.connect-timeout=0s");
    }

    @Test
    void shouldRejectNegativeTimeout() {
        assertSmtpFails("app.email.smtp.read-timeout", "app.email.smtp.read-timeout=-1s");
    }

    @Test
    void shouldRejectAuthWithoutUsername() {
        assertSmtpFails("app.email.smtp.username", "app.email.smtp.username=");
    }

    @Test
    void shouldRejectAuthWithoutPassword() {
        assertSmtpFails("app.email.smtp.password", "app.email.smtp.password=");
    }

    @Test
    void shouldAllowSmtpWithoutCredentialsWhenAuthIsDisabled() {
        smtpContext(
                "app.email.smtp.auth=false",
                "app.email.smtp.username=",
                "app.email.smtp.password="
        ).run(context -> assertThat(context.getStartupFailure()).isNull());
    }

    private ApplicationContextRunner smtpContext(String... overrides) {
        return contextRunner.withPropertyValues(
                MODE + "=SMTP",
                VERIFICATION_PAGE_URL + "=" + VALID_HTTPS_URL,
                "app.email.sender.address=no-reply@example.test",
                "app.email.sender.name=Support Trainer",
                "app.email.sender.reply-to=reply@example.test",
                "app.email.smtp.host=smtp.example.test",
                "app.email.smtp.port=587",
                "app.email.smtp.username=test-user",
                "app.email.smtp.password=test-password",
                "app.email.smtp.auth=true",
                "app.email.smtp.start-tls=true",
                "app.email.smtp.connect-timeout=5s",
                "app.email.smtp.read-timeout=6s",
                "app.email.smtp.write-timeout=7s"
        ).withPropertyValues(overrides);
    }

    private void assertInvalidUrl(String value) {
        assertConfigurationFails(
                VERIFICATION_PAGE_URL,
                MODE + "=DISABLED",
                VERIFICATION_PAGE_URL + "=" + value
        );
    }

    private void assertSmtpFails(String propertyName, String override) {
        smtpContext(override).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure).isNotNull();
            assertThat(failure).hasStackTraceContaining(propertyName);
        });
    }

    private void assertConfigurationFails(String propertyName, String... properties) {
        contextRunner.withPropertyValues(properties)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure).hasStackTraceContaining(propertyName);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    @Import({
            DisabledEmailVerificationSender.class,
            InMemoryEmailVerificationSender.class,
            SmtpEmailVerificationSender.class,
            SmtpMailSenderConfiguration.class,
            EmailVerificationEmailComposer.class
    })
    static class TestConfiguration {

        @Bean
        ApplicationTimeProvider applicationTimeProvider() {
            return new ApplicationTimeProvider(
                    Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC),
                    new TimeProperties(ZoneId.of("Europe/Rome"), ZoneOffset.UTC)
            );
        }
    }
}
