package it.zuperman.support_trainer.security.session;

import java.util.Objects;

import it.zuperman.support_trainer.common.enums.Role;

/**
 * Transient login result used only to build the session Authentication.
 * Must not be stored in the HTTP session.
 */
public record SessionLoginIdentity(Long userId, String email, Role role, long sessionVersion) {

    public SessionLoginIdentity {
        Objects.requireNonNull(userId, "userId must not be null");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be null or blank");
        }
        Objects.requireNonNull(role, "role must not be null");
        email = email.trim();
    }
}
