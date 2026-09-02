package it.zuperman.support_trainer.email.adapter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;
import it.zuperman.support_trainer.email.port.PasswordRecoverySender;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "mode", havingValue = "DISABLED", matchIfMissing = true)
public class DisabledPasswordRecoverySender implements PasswordRecoverySender {

    @Override
    public void send(PasswordRecoveryMessage message) {
        // Safe no-op: delivery is intentionally disabled.
    }
}
