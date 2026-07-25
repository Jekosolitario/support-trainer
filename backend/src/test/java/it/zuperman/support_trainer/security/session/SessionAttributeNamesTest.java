package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionAttributeNamesTest {

    @Test
    @DisplayName("Deve esporre la chiave applicativa authenticatedAt una sola volta")
    void shouldExposeAuthenticatedAtKey() {
        assertThat(SessionAttributeNames.AUTHENTICATED_AT).isEqualTo("authenticatedAt");
    }
}
