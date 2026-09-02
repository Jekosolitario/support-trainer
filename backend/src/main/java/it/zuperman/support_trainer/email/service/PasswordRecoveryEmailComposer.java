package it.zuperman.support_trainer.email.service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.email.model.PasswordRecoveryEmailContent;
import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;

@Component
public class PasswordRecoveryEmailComposer {

    static final String SUBJECT = "Reimposta la password di Support Trainer";
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter
            .ofPattern("d MMMM uuuu 'alle' HH:mm VV", Locale.ITALIAN);

    private final ApplicationTimeProvider timeProvider;

    public PasswordRecoveryEmailComposer(ApplicationTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public PasswordRecoveryEmailContent compose(PasswordRecoveryMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        String expiry = EXPIRY_FORMATTER.format(message.expiresAt().atZone(timeProvider.businessZone()));
        String body = """
                Ciao,

                abbiamo ricevuto una richiesta di reimpostazione della password per il tuo account Support Trainer.

                Apri il seguente link:
                %s

                Il link scade il %s.

                Se non hai richiesto questa operazione, puoi ignorare il messaggio. La password attuale resta invariata.

                Support Trainer
                """.formatted(message.recoveryUrl(), expiry);

        return new PasswordRecoveryEmailContent(SUBJECT, body);
    }
}
