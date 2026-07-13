package it.zuperman.support_trainer.security.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.zuperman.support_trainer.common.exception.AppException;

class BcryptLengthAwarePasswordEncoderTest {

    private final PasswordEncoder delegate = mock(PasswordEncoder.class);
    private final BcryptLengthAwarePasswordEncoder encoder = new BcryptLengthAwarePasswordEncoder(delegate);

    @Test
    void shouldRejectOversizedPasswordBeforeEncoding() {
        String password = "A1!" + "a".repeat(70);

        assertThatThrownBy(() -> encoder.encode(password))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getErrorCode()).isEqualTo("VALIDATION_ERROR");
                    assertThat(exception.getMessage()).isEqualTo(BcryptPasswordPolicy.MAX_LENGTH_MESSAGE);
                });
        verifyNoInteractions(delegate);
    }

    @Test
    void shouldRejectOversizedPasswordBeforeMatching() {
        String password = "A1!" + "€".repeat(24);

        assertThat(encoder.matches(password, "stored-hash")).isFalse();
        verifyNoInteractions(delegate);
    }

    @Test
    void shouldDelegateExactlySeventyTwoBytesWithoutTransformingThePassword() {
        String password = "A1!" + "a".repeat(69);
        given(delegate.encode(password)).willReturn("encoded-password");

        assertThat(encoder.encode(password)).isEqualTo("encoded-password");
        verify(delegate).encode(password);
    }

    @Test
    void shouldDelegateMatchingAtExactlySeventyTwoBytes() {
        String password = "A1!" + "€".repeat(23);
        given(delegate.matches(password, "stored-hash")).willReturn(true);

        assertThat(encoder.matches(password, "stored-hash")).isTrue();
        verify(delegate).matches(password, "stored-hash");
    }
}
