package it.zuperman.support_trainer.security.session;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;

/**
 * Minimal authenticated principal for the server-side session model.
 * Canonical identity is {@code userId}; {@code email} is informational only.
 * {@code sessionVersion} is the snapshot taken at authentication time.
 */
public final class AuthenticatedUserPrincipal implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    private final Long userId;
    private final String email;
    private final long sessionVersion;

    public AuthenticatedUserPrincipal(Long userId, String email) {
        this(userId, email, 0L);
    }

    public AuthenticatedUserPrincipal(Long userId, String email, long sessionVersion) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be null or blank");
        }
        this.userId = userId;
        this.email = email.trim();
        this.sessionVersion = sessionVersion;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthenticatedUserPrincipal that)) {
            return false;
        }
        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "AuthenticatedUserPrincipal[userId=" + userId + ", sessionVersion=" + sessionVersion + "]";
    }
}
