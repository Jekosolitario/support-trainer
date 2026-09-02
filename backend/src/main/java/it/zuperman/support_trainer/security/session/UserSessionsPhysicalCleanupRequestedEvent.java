package it.zuperman.support_trainer.security.session;

import java.util.Objects;

/**
 * Post-commit signal for best-effort physical Spring Session row removal.
 * Logical revocation is {@code User.sessionVersion}, not this cleanup.
 */
public record UserSessionsPhysicalCleanupRequestedEvent(Long userId) {

    public UserSessionsPhysicalCleanupRequestedEvent {
        Objects.requireNonNull(userId, "userId must not be null");
    }

    @Override
    public String toString() {
        return "UserSessionsPhysicalCleanupRequestedEvent[userId=" + userId + "]";
    }
}
