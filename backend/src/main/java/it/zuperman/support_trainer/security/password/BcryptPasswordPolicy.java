package it.zuperman.support_trainer.security.password;

import java.nio.charset.StandardCharsets;

public final class BcryptPasswordPolicy {

    public static final int MAX_UTF8_BYTES = 72;
    public static final String MAX_LENGTH_MESSAGE =
            "La password non può superare 72 byte in codifica UTF-8";

    private BcryptPasswordPolicy() {
    }

    public static int utf8Length(CharSequence password) {
        if (password == null) {
            return 0;
        }

        return password.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    public static boolean isWithinLimit(CharSequence password) {
        return password == null || utf8Length(password) <= MAX_UTF8_BYTES;
    }
}
