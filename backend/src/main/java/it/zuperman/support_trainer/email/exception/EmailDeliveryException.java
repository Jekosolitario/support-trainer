package it.zuperman.support_trainer.email.exception;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException() {
        super("Email verification delivery failed");
    }
}
