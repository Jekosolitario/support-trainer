package it.zuperman.support_trainer.auth.passwordrecovery;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenGeneratorTest {

    private final PasswordResetTokenGenerator generator = new PasswordResetTokenGenerator();

    @Test
    void shouldGenerateUrlSafeUnpaddedTokensWithAtLeast256Bits() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            String token = generator.generateRawToken();
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            assertThat(decoded).hasSize(PasswordResetTokenGenerator.TOKEN_ENTROPY_BYTES);
            assertThat(token).doesNotContain("=", "+", "/");
            assertThat(token).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(32);
    }
}
