package it.zuperman.support_trainer.security.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeConfiguration;
import it.zuperman.support_trainer.common.time.TimeProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesValidationTest {

    private static final String JWT_SECRET_PROPERTY = "app.security.jwt.secret";
    private static final String JWT_EXPIRATION_PROPERTY = "app.security.jwt.expiration";
    private static final String JWT_REFRESH_EXPIRATION_PROPERTY = "app.security.jwt.refresh-expiration";
    private static final String CORS_ORIGINS_PROPERTY = "app.cors.allowed-origins";
    private static final String BUSINESS_ZONE_PROPERTY = "app.time.business-zone";
    private static final String CLOCK_ZONE_PROPERTY = "app.time.clock-zone";
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "test-only-secret-key-32-bytes!!!".getBytes(StandardCharsets.UTF_8)
    );
    private static final List<String> VALID_PROPERTIES = List.of(
            JWT_SECRET_PROPERTY + "=" + TEST_SECRET,
            JWT_EXPIRATION_PROPERTY + "=1h",
            JWT_REFRESH_EXPIRATION_PROPERTY + "=7d",
            CORS_ORIGINS_PROPERTY + "=HTTP://LOCALHOST,https://FRONTEND.test:8443",
            BUSINESS_ZONE_PROPERTY + "=Europe/Rome",
            CLOCK_ZONE_PROPERTY + "=UTC"
    );

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void shouldBindValidConfiguration() {
        contextRunner.withPropertyValues(properties())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(JwtProperties.class);
                    assertThat(context).hasSingleBean(CorsProperties.class);
                    assertThat(context).hasSingleBean(TimeProperties.class);
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context).hasSingleBean(ApplicationTimeProvider.class);
                    assertThat(context.getBean(JwtProperties.class).expiration()).isEqualTo(Duration.ofHours(1));
                    assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                            .containsExactly("http://localhost", "https://frontend.test:8443");
                    assertThat(context.getBean(TimeProperties.class).businessZone())
                            .isEqualTo(ZoneId.of("Europe/Rome"));
                    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("UTC"));
                });
    }

    @Test
    void shouldFailWhenJwtSecretIsMissing() {
        assertConfigurationFails(JWT_SECRET_PROPERTY, propertiesWithout(JWT_SECRET_PROPERTY));
    }

    @Test
    void shouldFailWhenJwtSecretIsNotBase64() {
        assertConfigurationFails(JWT_SECRET_PROPERTY, propertiesWith(JWT_SECRET_PROPERTY, "not-valid-base64!"));
    }

    @Test
    void shouldFailWhenDecodedJwtSecretIsTooShort() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));
        assertConfigurationFails(JWT_SECRET_PROPERTY, propertiesWith(JWT_SECRET_PROPERTY, shortSecret));
    }

    @Test
    void shouldFailWhenAccessTokenDurationIsNotPositive() {
        assertConfigurationFails(JWT_EXPIRATION_PROPERTY, propertiesWith(JWT_EXPIRATION_PROPERTY, "0ms"));
    }

    @Test
    void shouldFailWhenRefreshTokenDurationIsNotPositive() {
        assertConfigurationFails(
                JWT_REFRESH_EXPIRATION_PROPERTY,
                propertiesWith(JWT_REFRESH_EXPIRATION_PROPERTY, "-1ms")
        );
    }

    @Test
    void shouldFailWhenRefreshTokenDurationIsNotGreaterThanAccessTokenDuration() {
        assertConfigurationFails(
                JWT_REFRESH_EXPIRATION_PROPERTY,
                propertiesWith(JWT_REFRESH_EXPIRATION_PROPERTY, "1h")
        );
    }

    @Test
    void shouldFailWhenCorsOriginsAreEmpty() {
        assertConfigurationFails(CORS_ORIGINS_PROPERTY, propertiesWith(CORS_ORIGINS_PROPERTY, ""));
    }

    @Test
    void shouldFailWhenCorsOriginIsInvalid() {
        assertConfigurationFails(CORS_ORIGINS_PROPERTY, propertiesWith(CORS_ORIGINS_PROPERTY, "ftp://frontend.test"));
    }

    @Test
    void shouldFailWhenCorsOriginContainsWildcard() {
        assertConfigurationFails(CORS_ORIGINS_PROPERTY, propertiesWith(CORS_ORIGINS_PROPERTY, "https://*.frontend.test"));
    }

    @Test
    void shouldFailWhenBusinessZoneIsInvalid() {
        contextRunner.withPropertyValues(propertiesWith(BUSINESS_ZONE_PROPERTY, "Not/A_Zone"))
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(failure).hasStackTraceContaining(BUSINESS_ZONE_PROPERTY);
                });
    }

    @Test
    void shouldFailWhenClockZoneDoesNotRepresentUtc() {
        assertConfigurationFails(CLOCK_ZONE_PROPERTY, propertiesWith(CLOCK_ZONE_PROPERTY, "Europe/Rome"));
    }

    @Test
    void shouldAllowFixedClockOverrideForTests() {
        Instant fixedInstant = Instant.parse("2026-07-13T15:30:45Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        contextRunner.withBean(Clock.class, () -> fixedClock)
                .withPropertyValues(properties())
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context.getBean(Clock.class)).isSameAs(fixedClock);
                    assertThat(context.getBean(ApplicationTimeProvider.class).nowInstant()).isEqualTo(fixedInstant);
                });
    }

    private void assertConfigurationFails(String expectedProperty, String... properties) {
        contextRunner.withPropertyValues(properties)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();
                    assertThat(causeForProperty(failure, expectedProperty))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining(expectedProperty);
                });
    }

    private static String[] properties() {
        return VALID_PROPERTIES.toArray(String[]::new);
    }

    private static String[] propertiesWith(String propertyName, String value) {
        return VALID_PROPERTIES.stream()
                .map(property -> property.startsWith(propertyName + "=") ? propertyName + "=" + value : property)
                .toArray(String[]::new);
    }

    private static String[] propertiesWithout(String propertyName) {
        return VALID_PROPERTIES.stream()
                .filter(property -> !property.startsWith(propertyName + "="))
                .toArray(String[]::new);
    }

    private static Throwable causeForProperty(Throwable throwable, String propertyName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IllegalArgumentException
                    && current.getMessage() != null
                    && current.getMessage().contains(propertyName)) {
                return current;
            }
            current = current.getCause();
        }
        return null;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, TimeProperties.class})
    @Import({TimeConfiguration.class, ApplicationTimeProvider.class})
    static class TestConfiguration {
    }
}
