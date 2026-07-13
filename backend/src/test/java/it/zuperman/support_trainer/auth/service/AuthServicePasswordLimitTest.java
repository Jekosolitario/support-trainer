package it.zuperman.support_trainer.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.repository.EmailVerificationTokenRepository;
import it.zuperman.support_trainer.client.repository.ClientProfileRepository;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.invite.service.InviteCodeService;
import it.zuperman.support_trainer.link.repository.ProfessionalClientLinkRepository;
import it.zuperman.support_trainer.professional.repository.ProfessionalProfileRepository;
import it.zuperman.support_trainer.security.jwt.JwtService;

class AuthServicePasswordLimitTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final AuthService authService = new AuthService(
            userRepository,
            mock(ProfessionalProfileRepository.class),
            mock(EmailVerificationTokenRepository.class),
            mock(ClientProfileRepository.class),
            mock(InviteCodeService.class),
            mock(PasswordEncoder.class),
            authenticationManager,
            mock(JwtService.class),
            mock(ProfessionalClientLinkRepository.class),
            mock(ApplicationTimeProvider.class)
    );

    @Test
    void shouldRejectOversizedLoginBeforeAuthenticationAndUserLookup() {
        LoginRequest request = new LoginRequest(
                "utente@example.com",
                "A1!" + "a".repeat(70)
        );

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenziali non valide");
        verifyNoInteractions(authenticationManager, userRepository);
    }
}
