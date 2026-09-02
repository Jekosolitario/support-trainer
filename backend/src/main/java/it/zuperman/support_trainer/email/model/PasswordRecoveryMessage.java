package it.zuperman.support_trainer.email.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PasswordRecoveryMessage(
        String recipient,
        String recoveryUrl,
        Instant expiresAt,
        UUID correlationId
) {

    public PasswordRecoveryMessage {
        requireText(recipient, "recipient");
        requireText(recoveryUrl, "recoveryUrl");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    @Override
    public String toString() {
        return "PasswordRecoveryMessage[correlationId=" + correlationId + "]";
    }
}
