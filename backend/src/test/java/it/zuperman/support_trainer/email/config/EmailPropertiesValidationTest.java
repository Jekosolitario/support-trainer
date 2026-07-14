package it.zuperman.support_trainer.email.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import it.zuperman.support_trainer.email.adapter.DisabledEmailVerificationSender;
import it.zuperman.support_trainer.email.adapter.InMemoryEmailVerificationSender;
import it.zuperman.support_trainer.email.port.EmailVerificationSender;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPropertiesValidationTest {

    private static final String MODE = "app.email.mode";
    private static final String VERIFICATION_PAGE_URL = "app.email.verification-page-url";
    private static final String VALID_URL = "https://frontend.example/verify-email";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldCreateOnlyDisabledSender() {
        contextRunner.withPropertyValues(MODE + "=DISABLED", VERIFICATION_PAGE_URL + "=" + VALID_URL)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(EmailVerificationSender.class);
                    assertThat(context.getBean(EmailVerificationSender.class))
                            .isInstanceOf(DisabledEmailVerificationSender.class);
                });
    }

    @Test
    void shouldCreateOnlyInMemorySender() {
        contextRunner.withPropertyValues(MODE + "=IN_MEMORY", VERIFICATION_PAGE_URL + "=" + VALID_URL)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(EmailVerificationSender.class);
                    assertThat(context.getBean(EmailVerificationSender.class))
                            .isInstanceOf(InMemoryEmailVerificationSender.class);
                });
    }

    @Test
    void shouldDefaultToDisabledModeWhenModeIsMissing() {
        contextRunner.withPropertyValues(VERIFICATION_PAGE_URL + "=" + VALID_URL)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context.getBean(EmailProperties.class).mode()).isEqualTo(EmailMode.DISABLED);
                    assertThat(context.getBean(EmailVerificationSender.class))
                            .isInstanceOf(DisabledEmailVerificationSender.class);
                });
    }

    @Test
    void shouldRejectUnsupportedMode() {
        assertConfigurationFails(MODE, MODE + "=SMTP", VERIFICATION_PAGE_URL + "=" + VALID_URL);
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
    void shouldAcceptAbsoluteVerificationPageUrlWithBasePath() {
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

    private void assertInvalidUrl(String value) {
        assertConfigurationFails(
                VERIFICATION_PAGE_URL,
                MODE + "=DISABLED",
                VERIFICATION_PAGE_URL + "=" + value
        );
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
    @Import({DisabledEmailVerificationSender.class, InMemoryEmailVerificationSender.class})
    static class TestConfiguration {
    }
}
