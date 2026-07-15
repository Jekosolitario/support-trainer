package it.zuperman.support_trainer.email.config;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpMailSenderConfigurationTest {

    @Test
    void shouldApplyTypedSmtpConfigurationToJavaMailSender() {
        EmailProperties properties = new EmailProperties(
                EmailMode.SMTP,
                URI.create("https://frontend.example/verify-email"),
                new EmailProperties.Sender("no-reply@example.test", "Support Trainer", "reply@example.test"),
                new EmailProperties.Smtp(
                        "smtp.example.test", 2525, "smtp-user", "smtp-password", true, true,
                        Duration.ofSeconds(3), Duration.ofSeconds(4), Duration.ofSeconds(5)
                )
        );

        JavaMailSenderImpl mailSender = new SmtpMailSenderConfiguration().smtpJavaMailSender(properties);

        assertThat(mailSender.getHost()).isEqualTo("smtp.example.test");
        assertThat(mailSender.getPort()).isEqualTo(2525);
        assertThat(mailSender.getUsername()).isEqualTo("smtp-user");
        assertThat(mailSender.getPassword()).isEqualTo("smtp-password");
        assertThat(mailSender.getDefaultEncoding()).isEqualTo("UTF-8");
        assertThat(mailSender.getJavaMailProperties())
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.connectiontimeout", "3000")
                .containsEntry("mail.smtp.timeout", "4000")
                .containsEntry("mail.smtp.writetimeout", "5000");
    }
}
