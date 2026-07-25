package it.zuperman.support_trainer.security.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    private static final String BUSINESS_ZONE_PROPERTY = "app.time.business-zone";
    private static final String CLOCK_ZONE_PROPERTY = "app.time.clock-zone";
    private static final List<String> VALID_PROPERTIES = List.of(
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
                    assertThat(context).hasSingleBean(TimeProperties.class);
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context).hasSingleBean(ApplicationTimeProvider.class);
                    assertThat(context.getBean(TimeProperties.class).businessZone())
                            .isEqualTo(ZoneId.of("Europe/Rome"));
                    assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("UTC"));
                });
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
    @EnableConfigurationProperties({TimeProperties.class})
    @Import({TimeConfiguration.class, ApplicationTimeProvider.class})
    static class TestConfiguration {
    }
}
