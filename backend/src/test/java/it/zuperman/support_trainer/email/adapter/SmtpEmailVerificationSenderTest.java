package it.zuperman.support_trainer.email.adapter;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.email.config.EmailMode;
import it.zuperman.support_trainer.email.config.EmailProperties;
import it.zuperman.support_trainer.email.exception.EmailDeliveryException;
import it.zuperman.support_trainer.email.model.EmailVerificationMessage;
import it.zuperman.support_trainer.email.model.EmailVerificationReason;
import it.zuperman.support_trainer.email.service.EmailVerificationEmailComposer;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailVerificationSenderTest {

    private static final String RECIPIENT = "recipient@example.test";
    private static final String URL = "https://frontend.example/verify-email#token=sensitive-token";

    @Mock
    private JavaMailSender mailSender;

    @Test
    void shouldSendPlainTextUtf8MessageWithConfiguredSenderAndReplyTo() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SmtpEmailVerificationSender sender = sender("reply@example.test");

        sender.send(message());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        InternetAddress from = (InternetAddress) sent.getFrom()[0];
        InternetAddress recipient = (InternetAddress) sent.getRecipients(Message.RecipientType.TO)[0];

        assertThat(from.getAddress()).isEqualTo("no-reply@example.test");
        assertThat(from.getPersonal()).isEqualTo("Support Trainer");
        assertThat(sent.getHeader("Reply-To")).containsExactly("reply@example.test");
        assertThat(recipient.getAddress()).isEqualTo(RECIPIENT);
        assertThat(sent.getSubject()).isEqualTo("Verifica il tuo indirizzo email - Support Trainer");
        assertThat(sent.getContentType()).containsIgnoringCase("text/plain");
        assertThat(sent.getContentType()).containsIgnoringCase("charset=utf-8");
        assertThat((String) sent.getContent()).contains(URL, "15 luglio 2026 alle 14:30 Europe/Rome");
    }

    @Test
    void shouldNotSetReplyToWhenItIsNotConfigured() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        SmtpEmailVerificationSender sender = sender(null);

        sender.send(message());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getHeader("Reply-To")).isNull();
    }

    @Test
    void shouldSanitizePreparationAndMailSendFailures() {
        when(mailSender.createMimeMessage()).thenThrow(new MailParseException("smtp.internal.example sensitive-token"));

        assertSanitizedFailure(sender("reply@example.test"));

        reset(mailSender);
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp.internal.example sensitive-token"))
                .when(mailSender).send(any(MimeMessage.class));

        assertSanitizedFailure(sender("reply@example.test"));
    }

    private void assertSanitizedFailure(SmtpEmailVerificationSender sender) {
        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Email verification delivery failed")
                .hasMessageNotContaining(RECIPIENT)
                .hasMessageNotContaining(URL)
                .hasMessageNotContaining("sensitive-token")
                .hasMessageNotContaining("smtp.internal.example")
                .hasMessageNotContaining("smtp-password")
                .satisfies(exception -> assertThat(exception.getCause()).isNull());
    }

    private SmtpEmailVerificationSender sender(String replyTo) {
        return new SmtpEmailVerificationSender(
                mailSender,
                new EmailProperties(
                        EmailMode.SMTP,
                        URI.create("https://frontend.example/verify-email"),
                        URI.create("https://frontend.example/reset-password"),
                        new EmailProperties.Sender("no-reply@example.test", "Support Trainer", replyTo),
                        new EmailProperties.Smtp(
                                "smtp.internal.example", 587, "smtp-user", "smtp-password", true, true,
                                Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5)
                        )
                ),
                new EmailVerificationEmailComposer(new ApplicationTimeProvider(
                        Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC),
                        new TimeProperties(ZoneId.of("Europe/Rome"), ZoneOffset.UTC)
                ))
        );
    }

    private EmailVerificationMessage message() {
        return new EmailVerificationMessage(
                RECIPIENT,
                URL,
                Instant.parse("2026-07-15T12:30:00Z"),
                EmailVerificationReason.REGISTRATION,
                UUID.fromString("9d04744f-a6e0-4cb7-b7da-8110a320168f")
        );
    }
}
