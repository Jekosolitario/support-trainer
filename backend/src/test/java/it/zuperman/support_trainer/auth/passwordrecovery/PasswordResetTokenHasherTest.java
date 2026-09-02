package it.zuperman.support_trainer.auth.passwordrecovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetTokenHasherTest {

    @Test
    void shouldHashRawTokenAsLowercaseSha256Hex() throws Exception {
        String rawToken = "password-recovery-raw-token";
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(PasswordResetTokenHasher.sha256Hex(rawToken))
                .isEqualTo(expected)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .doesNotContain(rawToken);
    }

    @Test
    void shouldRejectBlankRawToken() {
        assertThatThrownBy(() -> PasswordResetTokenHasher.sha256Hex(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rawToken must not be blank");
    }
}
