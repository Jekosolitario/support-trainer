package it.zuperman.support_trainer.security.password;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BcryptPasswordPolicyTest {

    @Test
    void shouldAcceptExactlySeventyTwoAsciiBytes() {
        String password = "A1!" + "a".repeat(69);

        assertThat(password).hasSize(72);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(BcryptPasswordPolicy.utf8Length(password)).isEqualTo(72);
        assertThat(BcryptPasswordPolicy.isWithinLimit(password)).isTrue();
    }

    @Test
    void shouldRejectSeventyThreeAsciiBytes() {
        String password = "A1!" + "a".repeat(70);

        assertThat(password).hasSize(73);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);
        assertThat(BcryptPasswordPolicy.isWithinLimit(password)).isFalse();
    }

    @Test
    void shouldAcceptUnicodePasswordOfExactlySeventyTwoUtf8Bytes() {
        String password = "A1!" + "€".repeat(23);

        assertThat(password).hasSize(26);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(BcryptPasswordPolicy.isWithinLimit(password)).isTrue();
    }

    @Test
    void shouldRejectUnicodePasswordUnderSeventyTwoCharactersButOverSeventyTwoUtf8Bytes() {
        String password = "A1!" + "€".repeat(24);

        assertThat(password).hasSize(27);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(75);
        assertThat(BcryptPasswordPolicy.isWithinLimit(password)).isFalse();
    }
}
