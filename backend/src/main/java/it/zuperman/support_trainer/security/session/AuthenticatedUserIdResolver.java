package it.zuperman.support_trainer.security.session;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Resolves the canonical user id from a session {@link Authentication}.
 * Accepts only {@link AuthenticatedUserPrincipal}; any other principal is rejected.
 */
@Component
public final class AuthenticatedUserIdResolver {

    public Optional<Long> findUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
            return Optional.of(authenticatedUserPrincipal.getUserId());
        }
        return Optional.empty();
    }

    public Long requireUserId(Authentication authentication) {
        return findUserId(authentication).orElseThrow(() -> new IllegalArgumentException(
                "Authentication does not contain a canonical user id"
        ));
    }
}
