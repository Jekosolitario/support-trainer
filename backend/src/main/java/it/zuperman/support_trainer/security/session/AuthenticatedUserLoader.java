package it.zuperman.support_trainer.security.session;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import it.zuperman.support_trainer.common.entity.User;
import it.zuperman.support_trainer.common.exception.AppException;
import it.zuperman.support_trainer.common.repository.UserRepository;

/**
 * Loads the canonical authenticated {@link User} by session userId.
 * Does not resolve identity from email or {@link Authentication#getName()}.
 */
@Component
public final class AuthenticatedUserLoader {

    private final AuthenticatedUserIdResolver userIdResolver;
    private final UserRepository userRepository;

    public AuthenticatedUserLoader(
            AuthenticatedUserIdResolver userIdResolver,
            UserRepository userRepository
    ) {
        this.userIdResolver = userIdResolver;
        this.userRepository = userRepository;
    }

    public User requireAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = userIdResolver.findUserId(authentication)
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Utente non autenticato"
                ));

        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATED_USER_NOT_FOUND",
                        "Utente autenticato non trovato"
                ));
    }
}
