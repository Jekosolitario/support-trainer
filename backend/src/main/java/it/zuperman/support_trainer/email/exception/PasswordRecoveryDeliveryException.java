package it.zuperman.support_trainer.email.exception;

public class PasswordRecoveryDeliveryException extends RuntimeException {

    public PasswordRecoveryDeliveryException() {
        super("Email recovery delivery failed");
    }
}
