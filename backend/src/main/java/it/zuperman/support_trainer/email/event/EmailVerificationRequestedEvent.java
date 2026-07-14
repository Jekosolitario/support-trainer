package it.zuperman.support_trainer.email.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import it.zuperman.support_trainer.email.model.EmailVerificationReason;

public record EmailVerificationRequestedEvent(
        String recipient,
        String token,
        Instant expiresAt,
        EmailVerificationReason reason,
        UUID correlationId
) {

    private static final int MAX_TOKEN_LENGTH = 500;

    public EmailVerificationRequestedEvent {
        requireText(recipient, "recipient");
        requireText(token, "token");
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("token must not exceed 500 characters");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "EmailVerificationRequestedEvent[correlationId=" + correlationId + ", reason=" + reason + "]";
    }
}
