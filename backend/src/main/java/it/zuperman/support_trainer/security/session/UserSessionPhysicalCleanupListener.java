package it.zuperman.support_trainer.security.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Best-effort physical Spring Session cleanup after password reset commit.
 * Failures must not affect the already committed password/token/sessionVersion change.
 */
@Component
public class UserSessionPhysicalCleanupListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserSessionPhysicalCleanupListener.class);

    private final UserSessionRevocationService userSessionRevocationService;

    public UserSessionPhysicalCleanupListener(UserSessionRevocationService userSessionRevocationService) {
        this.userSessionRevocationService = userSessionRevocationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onUserSessionsPhysicalCleanupRequested(UserSessionsPhysicalCleanupRequestedEvent event) {
        try {
            userSessionRevocationService.revokeAllSessions(event.userId());
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Physical session cleanup failed userId={} errorType={}",
                    event.userId(),
                    exception.getClass().getSimpleName()
            );
        }
    }
}
