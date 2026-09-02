package it.zuperman.support_trainer.email.model;

import java.util.Objects;

public record PasswordRecoveryEmailContent(String subject, String body) {

    public PasswordRecoveryEmailContent {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("body must not be blank");
        }
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(body, "body must not be null");
    }

    @Override
    public String toString() {
        return "PasswordRecoveryEmailContent[subject=" + subject + "]";
    }
}
