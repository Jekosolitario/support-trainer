package it.zuperman.support_trainer.security.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull @DurationUnit(ChronoUnit.MILLIS) Duration expiration,
        @NotNull @DurationUnit(ChronoUnit.MILLIS) Duration refreshExpiration
) {

    private static final int MINIMUM_HMAC_KEY_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.security.jwt.secret must not be blank");
        }

        secret = secret.trim();
        byte[] decodedSecret;
        try {
            decodedSecret = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("app.security.jwt.secret must be valid Base64", exception);
        }

        if (decodedSecret.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "app.security.jwt.secret must decode to at least " + MINIMUM_HMAC_KEY_BYTES + " bytes"
            );
        }

        validateDuration("app.security.jwt.expiration", expiration);
        validateDuration("app.security.jwt.refresh-expiration", refreshExpiration);
        if (refreshExpiration.compareTo(expiration) <= 0) {
            throw new IllegalArgumentException(
                    "app.security.jwt.refresh-expiration must be greater than app.security.jwt.expiration"
            );
        }
    }

    private static void validateDuration(String propertyName, Duration duration) {
        if (duration == null || duration.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException(propertyName + " must be greater than zero milliseconds");
        }
    }
}
