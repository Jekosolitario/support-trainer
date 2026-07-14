package it.zuperman.support_trainer.email.model;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.email.event.EmailVerificationRequestedEvent;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationSensitiveModelTest {

    @Test
    void eventToStringShouldContainOnlyNonSensitiveIdentifiers() {
        UUID correlationId = UUID.randomUUID();
        EmailVerificationRequestedEvent event = new EmailVerificationRequestedEvent(
                "recipient@example.com",
                "sensitive-token",
                Instant.parse("2026-07-15T12:00:00Z"),
                EmailVerificationReason.REGISTRATION,
                correlationId
        );

        assertThat(event.toString())
                .contains(correlationId.toString(), "REGISTRATION")
                .doesNotContain(event.recipient(), event.token());
    }

    @Test
    void messageToStringShouldContainOnlyNonSensitiveIdentifiers() {
        UUID correlationId = UUID.randomUUID();
        EmailVerificationMessage message = new EmailVerificationMessage(
                "recipient@example.com",
                "https://frontend.example/verify-email#token=sensitive-token",
                Instant.parse("2026-07-15T12:00:00Z"),
                EmailVerificationReason.RESEND,
                correlationId
        );

        assertThat(message.toString())
                .contains(correlationId.toString(), "RESEND")
                .doesNotContain(message.recipient(), message.verificationUrl(), "sensitive-token");
    }
}
