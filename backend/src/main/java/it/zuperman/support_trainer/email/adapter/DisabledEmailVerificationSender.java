package it.zuperman.support_trainer.email.adapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.email.model.EmailVerificationMessage;
import it.zuperman.support_trainer.email.port.EmailVerificationSender;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "mode", havingValue = "DISABLED", matchIfMissing = true)
public class DisabledEmailVerificationSender implements EmailVerificationSender {

    @Override
    public void send(EmailVerificationMessage message) {
        // Safe no-op: delivery is intentionally disabled.
    }
}
