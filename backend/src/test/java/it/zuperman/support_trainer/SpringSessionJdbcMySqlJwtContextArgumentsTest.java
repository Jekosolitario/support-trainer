package it.zuperman.support_trainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.security.config.JwtProperties;

/**
 * Guards Lot 1 MySQL spring-context JWT arguments without starting a MySQL instance.
 */
class SpringSessionJdbcMySqlJwtContextArgumentsTest {

    @Test
    @DisplayName("Il contesto MySQL deve fornire tutte le proprietà obbligatorie JwtProperties")
    void mysqlContextMustSupplyAllRequiredJwtProperties() {
        assertThat(SpringSessionJdbcMySqlIntegrationTest.jwtContextArguments())
                .containsExactly(
                        "--app.security.jwt.secret=" + SpringSessionJdbcMySqlIntegrationTest.JWT_SECRET,
                        "--app.security.jwt.expiration=" + SpringSessionJdbcMySqlIntegrationTest.JWT_EXPIRATION,
                        "--app.security.jwt.refresh-expiration="
                                + SpringSessionJdbcMySqlIntegrationTest.JWT_REFRESH_EXPIRATION
                );

        assertThat(SpringSessionJdbcMySqlIntegrationTest.JWT_EXPIRATION).isEqualTo("1h");
        assertThat(SpringSessionJdbcMySqlIntegrationTest.JWT_REFRESH_EXPIRATION).isEqualTo("7d");

        assertThatCode(() -> new JwtProperties(
                SpringSessionJdbcMySqlIntegrationTest.JWT_SECRET,
                Duration.ofHours(1),
                Duration.ofDays(7)
        )).doesNotThrowAnyException();
    }
}
