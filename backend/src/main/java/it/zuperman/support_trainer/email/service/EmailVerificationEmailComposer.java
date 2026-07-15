package it.zuperman.support_trainer.email.service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.email.model.EmailVerificationEmailContent;
import it.zuperman.support_trainer.email.model.EmailVerificationMessage;

@Component
public class EmailVerificationEmailComposer {

    private static final String SUBJECT = "Verifica il tuo indirizzo email - Support Trainer";
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter
            .ofPattern("d MMMM uuuu 'alle' HH:mm VV", Locale.ITALIAN);

    private final ApplicationTimeProvider timeProvider;

    public EmailVerificationEmailComposer(ApplicationTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public EmailVerificationEmailContent compose(EmailVerificationMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        String expiry = EXPIRY_FORMATTER.format(message.expiresAt().atZone(timeProvider.businessZone()));
        String body = """
                Ciao,

                abbiamo ricevuto una richiesta di verifica per il tuo indirizzo email.

                Apri il seguente link:
                %s

                Il link scade il %s.

                Se non hai richiesto questa verifica, puoi ignorare il messaggio.

                Support Trainer
                """.formatted(message.verificationUrl(), expiry);

        return new EmailVerificationEmailContent(SUBJECT, body);
    }
}
