package it.zuperman.support_trainer.email.port;

import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;

public interface PasswordRecoverySender {

    void send(PasswordRecoveryMessage message);
}
