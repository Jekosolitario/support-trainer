package it.zuperman.support_trainer.email.adapter;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.email.config.EmailProperties;
import it.zuperman.support_trainer.email.exception.PasswordRecoveryDeliveryException;
import it.zuperman.support_trainer.email.model.PasswordRecoveryEmailContent;
import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;
import it.zuperman.support_trainer.email.port.PasswordRecoverySender;
import it.zuperman.support_trainer.email.service.PasswordRecoveryEmailComposer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "mode", havingValue = "SMTP")
public class SmtpPasswordRecoverySender implements PasswordRecoverySender {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    private final PasswordRecoveryEmailComposer composer;

    public SmtpPasswordRecoverySender(
            JavaMailSender mailSender,
            EmailProperties emailProperties,
            PasswordRecoveryEmailComposer composer
    ) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
        this.composer = composer;
    }

    @Override
    public void send(PasswordRecoveryMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            EmailProperties.Sender sender = emailProperties.sender();
            PasswordRecoveryEmailContent content = composer.compose(message);

            helper.setFrom(sender.address(), sender.name());
            if (sender.replyTo() != null) {
                helper.setReplyTo(sender.replyTo());
            }
            helper.setTo(message.recipient());
            helper.setSubject(content.subject());
            helper.setText(content.body(), false);
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException | UnsupportedEncodingException exception) {
            throw new PasswordRecoveryDeliveryException();
        }
    }
}
