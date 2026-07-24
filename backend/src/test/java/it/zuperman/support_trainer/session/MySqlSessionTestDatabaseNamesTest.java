package it.zuperman.support_trainer.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MySqlSessionTestDatabaseNamesTest {

    @Test
    @DisplayName("Deve accettare un nome dedicato valido")
    void shouldAcceptValidDedicatedName() {
        assertThat(MySqlSessionTestDatabaseNames.requireValid("support_trainer_session_test_empty"))
                .isEqualTo("support_trainer_session_test_empty");
        assertThat(MySqlSessionTestDatabaseNames.quoteIdentifier("support_trainer_session_test_from_v6"))
                .isEqualTo("`support_trainer_session_test_from_v6`");
    }

    @Test
    @DisplayName("Deve rifiutare un nome senza prefisso obbligatorio")
    void shouldRejectNameWithoutRequiredPrefix() {
        assertThatThrownBy(() -> MySqlSessionTestDatabaseNames.requireValid("st_session_empty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("support_trainer_session_test_");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "information_schema", "performance_schema", "sys", "MYSQL"})
    @DisplayName("Deve rifiutare gli schema di sistema")
    void shouldRejectSystemSchemas(String systemSchema) {
        assertThatThrownBy(() -> MySqlSessionTestDatabaseNames.requireValid(systemSchema))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("system schema");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "support_trainer_session_test_empty;drop",
            "support_trainer_session_test_empty-db",
            "support_trainer_session_test_Empty",
            "support_trainer_session_test_empty/db",
            "support_trainer_session_test_empty db",
            "support_trainer_session_test_empty`"
    })
    @DisplayName("Deve rifiutare caratteri SQL o separatori non ammessi")
    void shouldRejectSqlOrSeparatorCharacters(String unsafeName) {
        assertThatThrownBy(() -> MySqlSessionTestDatabaseNames.requireValid(unsafeName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Deve rifiutare due nomi uguali nella stessa coppia")
    void shouldRejectIdenticalPair() {
        assertThatThrownBy(() -> MySqlSessionTestDatabaseNames.requireDistinctPair(
                "support_trainer_session_test_empty",
                "support_trainer_session_test_empty"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinct");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rifiutare valori null, blank o vuoti")
    void shouldRejectNullBlankOrEmpty(String invalidName) {
        assertThatThrownBy(() -> MySqlSessionTestDatabaseNames.requireValid(invalidName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("Deve accettare una coppia distinta di nomi validi")
    void shouldAcceptDistinctValidPair() {
        assertThat(MySqlSessionTestDatabaseNames.requireDistinctPair(
                MySqlSessionTestDatabaseNames.DEFAULT_EMPTY_SCHEMA,
                MySqlSessionTestDatabaseNames.DEFAULT_FROM_V6_SCHEMA
        )).containsExactly(
                "support_trainer_session_test_empty",
                "support_trainer_session_test_from_v6"
        );
    }
}
