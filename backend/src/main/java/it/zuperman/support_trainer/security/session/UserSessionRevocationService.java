package it.zuperman.support_trainer.security.session;

import java.util.Map;
import java.util.Objects;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Best-effort physical deletion of Spring Session JDBC rows for a principal.
 * This is not the password-reset security boundary: authenticated requests are
 * rejected when {@code AuthenticatedUserPrincipal.sessionVersion} differs from
 * {@code User.sessionVersion}, even if a session row still exists.
 */
@Service
public class UserSessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public UserSessionRevocationService(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository
    ) {
        this.sessionRepository = sessionRepository;
    }

    public void revokeAllSessions(Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        String principalName = userId.toString();
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principalName);
        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
        }
    }
}
