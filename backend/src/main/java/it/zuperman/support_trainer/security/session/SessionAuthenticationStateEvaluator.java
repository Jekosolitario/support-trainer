package it.zuperman.support_trainer.security.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;

/**
 * Pure readiness and absolute-timeout evaluator for the future session authentication model.
 * Not a Servlet filter and not registered in the current JWT {@code SecurityFilterChain}.
 */
@Component
public final class SessionAuthenticationStateEvaluator {

    static final Duration ABSOLUTE_SESSION_DURATION = Duration.ofHours(8);

    private final UserRepository userRepository;
    private final ApplicationTimeProvider timeProvider;
    private final AuthenticatedUserIdResolver userIdResolver;

    public SessionAuthenticationStateEvaluator(
            UserRepository userRepository,
            ApplicationTimeProvider timeProvider,
            AuthenticatedUserIdResolver userIdResolver
    ) {
        this.userRepository = userRepository;
        this.timeProvider = timeProvider;
        this.userIdResolver = userIdResolver;
    }

    /**
     * @param authentication current authentication
     * @param authenticatedAtAttribute value of {@link SessionAttributeNames#AUTHENTICATED_AT}
     * @return {@code true} only when snapshot, role coherence and absolute timeout all pass
     */
    public boolean isAuthenticationStillValid(Authentication authentication, Object authenticatedAtAttribute) {
        Optional<Long> userId = userIdResolver.findUserId(authentication);
        if (userId.isEmpty()) {
            return false;
        }

        Optional<UserSecuritySnapshot> snapshot = userRepository.findSecuritySnapshotById(userId.get());
        if (snapshot.isEmpty()) {
            return false;
        }

        UserSecuritySnapshot securitySnapshot = snapshot.get();
        if (securitySnapshot.getAccountStatus() != AccountStatus.ACTIVE) {
            return false;
        }
        if (!Boolean.TRUE.equals(securitySnapshot.getEmailVerified())) {
            return false;
        }
        if (!roleMatchesAuthority(authentication, securitySnapshot)) {
            return false;
        }

        Instant authenticatedAt = readAuthenticatedAt(authenticatedAtAttribute);
        if (authenticatedAt == null) {
            return false;
        }

        Instant expiresAt = authenticatedAt.plus(ABSOLUTE_SESSION_DURATION);
        Instant now = timeProvider.nowInstant();
        return now.isBefore(expiresAt);
    }

    private static boolean roleMatchesAuthority(
            Authentication authentication,
            UserSecuritySnapshot snapshot
    ) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null || authorities.size() != 1) {
            return false;
        }
        String authority = authorities.iterator().next().getAuthority();
        return snapshot.getRole().name().equals(authority);
    }

    private static Instant readAuthenticatedAt(Object authenticatedAtAttribute) {
        if (authenticatedAtAttribute instanceof Instant instant) {
            return instant;
        }
        return null;
    }
}
