package it.zuperman.support_trainer.security.session;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Resolves the canonical user id from a future session {@link Authentication}.
 * Prefer {@link AuthenticatedUserPrincipal}; otherwise parse {@link Authentication#getName()}.
 */
@Component
public final class AuthenticatedUserIdResolver {

    public Optional<Long> findUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal authenticatedUserPrincipal) {
            return Optional.of(authenticatedUserPrincipal.getUserId());
        }

        String name = authentication.getName();
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Long.valueOf(name.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Long requireUserId(Authentication authentication) {
        return findUserId(authentication).orElseThrow(() -> new IllegalArgumentException(
                "Authentication does not contain a canonical user id"
        ));
    }
}
