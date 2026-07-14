package it.zuperman.support_trainer.email.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EmailVerificationMessage(
        String recipient,
        String verificationUrl,
        Instant expiresAt,
        EmailVerificationReason reason,
        UUID correlationId
) {

    public EmailVerificationMessage {
        requireText(recipient, "recipient");
        requireText(verificationUrl, "verificationUrl");
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
        return "EmailVerificationMessage[correlationId=" + correlationId + ", reason=" + reason + "]";
    }
}
