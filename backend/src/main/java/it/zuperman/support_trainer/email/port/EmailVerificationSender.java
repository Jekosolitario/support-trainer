package it.zuperman.support_trainer.email.port;

import it.zuperman.support_trainer.email.model.EmailVerificationMessage;

public interface EmailVerificationSender {

    void send(EmailVerificationMessage message);
}
