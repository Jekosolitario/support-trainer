package it.zuperman.support_trainer.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import io.jsonwebtoken.Claims;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;
import it.zuperman.support_trainer.security.config.JwtProperties;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTimeTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2030-07-13T15:30:45Z");
    private static final Duration ACCESS_DURATION = Duration.ofHours(1);
    private static final Duration REFRESH_DURATION = Duration.ofDays(7);
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-only-secret-key-32-bytes!!!".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void shouldGenerateDeterministicAccessTokenDates() {
        JwtService jwtService = jwtService(FIXED_INSTANT);

        String token = jwtService.generateAccessToken(userDetails());

        assertThat(jwtService.extractClaim(token, Claims::getIssuedAt)).isEqualTo(Date.from(FIXED_INSTANT));
        assertThat(jwtService.extractExpiration(token)).isEqualTo(Date.from(FIXED_INSTANT.plus(ACCESS_DURATION)));
    }

    @Test
    void shouldKeepRefreshTokenDurationUnchanged() {
        JwtService jwtService = jwtService(FIXED_INSTANT);

        String token = jwtService.generateRefreshToken(userDetails());
        Date issuedAt = jwtService.extractClaim(token, Claims::getIssuedAt);
        Date expiration = jwtService.extractExpiration(token);

        assertThat(expiration.getTime() - issuedAt.getTime()).isEqualTo(REFRESH_DURATION.toMillis());
    }

    private static JwtService jwtService(Instant instant) {
        TimeProperties timeProperties = new TimeProperties(ZoneId.of("Europe/Rome"), ZoneId.of("UTC"));
        ApplicationTimeProvider timeProvider = new ApplicationTimeProvider(
                Clock.fixed(instant, ZoneOffset.UTC),
                timeProperties
        );
        return new JwtService(new JwtProperties(SECRET, ACCESS_DURATION, REFRESH_DURATION), timeProvider);
    }

    private static UserDetails userDetails() {
        return org.springframework.security.core.userdetails.User
                .withUsername("time-test@example.com")
                .password("encoded-password")
                .authorities("PROFESSIONAL")
                .build();
    }
}
