package it.zuperman.support_trainer.email.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.email.model.EmailVerificationEmailContent;
import it.zuperman.support_trainer.email.model.EmailVerificationMessage;
import it.zuperman.support_trainer.email.model.EmailVerificationReason;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationEmailComposerTest {

    private static final String URL = "https://frontend.example/verify-email#token=encoded-token";

    private final EmailVerificationEmailComposer composer = new EmailVerificationEmailComposer(
            new ApplicationTimeProvider(
                    Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC),
                    new TimeProperties(ZoneId.of("Europe/Rome"), ZoneOffset.UTC)
            )
    );

    @Test
    void shouldComposeNeutralItalianPlainTextWithBusinessZoneExpiry() {
        EmailVerificationEmailContent content = composer.compose(message(EmailVerificationReason.REGISTRATION));

        assertThat(content.subject()).isEqualTo("Verifica il tuo indirizzo email - Support Trainer");
        assertThat(content.body())
                .contains("Ciao,", "richiesta di verifica", URL,
                        "15 gennaio 2026 alle 14:30 Europe/Rome", "puoi ignorare", "Support Trainer")
                .doesNotContain("CLIENT", "PROFESSIONAL", "JWT", "nota", "sanitario");
        assertThat(occurrences(content.body(), URL)).isEqualTo(1);
    }

    @Test
    void shouldKeepContentNeutralForResend() {
        EmailVerificationEmailContent registration = composer.compose(message(EmailVerificationReason.REGISTRATION));
        EmailVerificationEmailContent resend = composer.compose(message(EmailVerificationReason.RESEND));

        assertThat(resend).isEqualTo(registration);
    }

    private EmailVerificationMessage message(EmailVerificationReason reason) {
        return new EmailVerificationMessage(
                "recipient@example.test",
                URL,
                Instant.parse("2026-01-15T13:30:00Z"),
                reason,
                UUID.fromString("f77b58ac-3b65-4c26-8b81-ddce1ca1f07b")
        );
    }

    private int occurrences(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
