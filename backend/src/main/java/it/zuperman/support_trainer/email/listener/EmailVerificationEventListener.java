package it.zuperman.support_trainer.email.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import it.zuperman.support_trainer.email.event.EmailVerificationRequestedEvent;
import it.zuperman.support_trainer.email.model.EmailVerificationMessage;
import it.zuperman.support_trainer.email.port.EmailVerificationSender;
import it.zuperman.support_trainer.email.service.EmailVerificationLinkBuilder;

@Component
public class EmailVerificationEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationEventListener.class);

    private final EmailVerificationLinkBuilder linkBuilder;
    private final EmailVerificationSender sender;

    public EmailVerificationEventListener(
            EmailVerificationLinkBuilder linkBuilder,
            EmailVerificationSender sender
    ) {
        this.linkBuilder = linkBuilder;
        this.sender = sender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        try {
            String verificationUrl = linkBuilder.build(event.token());
            sender.send(new EmailVerificationMessage(
                    event.recipient(),
                    verificationUrl,
                    event.expiresAt(),
                    event.reason(),
                    event.correlationId()
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Email verification delivery failed correlationId={} reason={} errorType={}",
                    event.correlationId(),
                    event.reason(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
