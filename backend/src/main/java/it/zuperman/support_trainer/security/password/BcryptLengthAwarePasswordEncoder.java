package it.zuperman.support_trainer.security.password;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.zuperman.support_trainer.common.exception.AppException;

public class BcryptLengthAwarePasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;

    public BcryptLengthAwarePasswordEncoder(PasswordEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        requireWithinLimit(rawPassword);
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (!BcryptPasswordPolicy.isWithinLimit(rawPassword)) {
            return false;
        }

        return delegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    private static void requireWithinLimit(CharSequence rawPassword) {
        if (!BcryptPasswordPolicy.isWithinLimit(rawPassword)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_ERROR",
                    BcryptPasswordPolicy.MAX_LENGTH_MESSAGE
            );
        }
    }
}
