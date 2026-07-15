package it.zuperman.support_trainer.email.config;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MailpitProfileConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(mailpitProperties());

    @Test
    void shouldLoadTrackedMailpitProfileWithoutCredentialsOrNetwork() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            EmailProperties properties = context.getBean(EmailProperties.class);

            assertThat(properties.mode()).isEqualTo(EmailMode.SMTP);
            assertThat(properties.verificationPageUrl().toString())
                    .isEqualTo("http://localhost:5173/verify-email");
            assertThat(properties.smtp().host()).isEqualTo("localhost");
            assertThat(properties.smtp().port()).isEqualTo(1025);
            assertThat(properties.smtp().auth()).isFalse();
            assertThat(properties.smtp().startTls()).isFalse();
            assertThat(properties.smtp().username()).isNull();
            assertThat(properties.smtp().password()).isNull();
        });
    }

    private static String[] mailpitProperties() {
        try {
            Properties properties = PropertiesLoaderUtils.loadProperties(
                    new ClassPathResource("application-mailpit.properties")
            );
            return properties.stringPropertyNames().stream()
                    .map(name -> name + "=" + properties.getProperty(name))
                    .toArray(String[]::new);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the Mailpit profile", exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailProperties.class)
    static class TestConfiguration {
    }
}
