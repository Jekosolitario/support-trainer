package it.zuperman.support_trainer.email.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.email.model.PasswordRecoveryEmailContent;
import it.zuperman.support_trainer.email.model.PasswordRecoveryMessage;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRecoveryEmailComposerTest {

    private static final String URL = "https://frontend.example/reset-password#token=encoded-token";

    private final PasswordRecoveryEmailComposer composer = new PasswordRecoveryEmailComposer(
            new ApplicationTimeProvider(
                    Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC),
                    new TimeProperties(ZoneId.of("Europe/Rome"), ZoneOffset.UTC)
            )
    );

    @Test
    void shouldComposeNeutralItalianPlainTextWithBusinessZoneExpiry() {
        PasswordRecoveryEmailContent content = composer.compose(message());

        assertThat(content.subject()).isEqualTo("Reimposta la password di Support Trainer");
        assertThat(content.body())
                .contains("Ciao,", "reimpostazione della password", URL,
                        "15 gennaio 2026 alle 14:30 Europe/Rome", "puoi ignorare", "Support Trainer")
                .doesNotContain("CLIENT", "PROFESSIONAL", "JWT", "sensitive-token");
        assertThat(content.toString()).doesNotContain(URL, "recipient@example.test");
    }

    private PasswordRecoveryMessage message() {
        return new PasswordRecoveryMessage(
                "recipient@example.test",
                URL,
                Instant.parse("2026-01-15T13:30:00Z"),
                UUID.fromString("f77b58ac-3b65-4c26-8b81-ddce1ca1f07b")
        );
    }
}
