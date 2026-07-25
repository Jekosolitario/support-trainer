package it.zuperman.support_trainer.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.security.UserReadinessValidator;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;

class AuthServicePasswordLimitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final AuthService authService = new AuthService(
            userRepository,
            mock(EmailVerificationTokenRepository.class),
            mock(PasswordEncoder.class),
            authenticationManager,
            mock(ApplicationTimeProvider.class),
            mock(ApplicationEventPublisher.class),
            mock(RegistrationPersistenceService.class),
            new UserReadinessValidator()
    );

    @Test
    void shouldRejectOversizedLoginBeforeAuthenticationAndUserLookup() {
        LoginRequest request = new LoginRequest(
                "utente@example.com",
                "A1!" + "a".repeat(70)
        );

        assertThatThrownBy(() -> authService.authenticateForSession(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenziali non valide");
        verifyNoInteractions(authenticationManager, userRepository);
    }
}
