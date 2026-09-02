package it.zuperman.support_trainer.email.model;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.email.event.PasswordRecoveryRequestedEvent;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoverySensitiveModelTest {

    @Test
    void eventToStringShouldContainOnlyNonSensitiveIdentifiers() {
        UUID correlationId = UUID.randomUUID();
        PasswordRecoveryRequestedEvent event = new PasswordRecoveryRequestedEvent(
                "recipient@example.com",
                "sensitive-token",
                Instant.parse("2026-07-15T12:00:00Z"),
                correlationId
        );

        assertThat(event.toString())
                .contains(correlationId.toString())
                .doesNotContain(event.recipient(), event.token(), "sensitive-token");
    }

    @Test
    void messageToStringShouldContainOnlyNonSensitiveIdentifiers() {
        UUID correlationId = UUID.randomUUID();
        PasswordRecoveryMessage message = new PasswordRecoveryMessage(
                "recipient@example.com",
                "https://frontend.example/reset-password#token=sensitive-token",
                Instant.parse("2026-07-15T12:00:00Z"),
                correlationId
        );

        assertThat(message.toString())
                .contains(correlationId.toString())
                .doesNotContain(message.recipient(), message.recoveryUrl(), "sensitive-token");
    }
}
