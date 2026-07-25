package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Role;
import it.zuperman.support_trainer.common.repository.UserRepository;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import it.zuperman.support_trainer.common.time.TimeProperties;

@ExtendWith(MockitoExtension.class)
class SessionAuthenticationStateEvaluatorTest {

    private static final Instant AUTHENTICATED_AT = Instant.parse("2026-07-25T10:00:00Z");

    @Mock
    private UserRepository userRepository;

    private SessionAuthenticationStateEvaluator evaluator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AUTHENTICATED_AT.plus(Duration.ofHours(1)), ZoneOffset.UTC);
        ApplicationTimeProvider timeProvider = new ApplicationTimeProvider(
                clock,
                new TimeProperties(ZoneOffset.ofHours(2), ZoneOffset.UTC)
        );
        evaluator = new SessionAuthenticationStateEvaluator(
                userRepository,
                timeProvider,
                new AuthenticatedUserIdResolver()
        );
    }

    @Test
    @DisplayName("ACTIVE + verified + ruolo coerente prima delle 8h deve essere valido")
    void shouldAcceptReadyAuthenticationBeforeAbsoluteTimeout() {
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.CLIENT,
                AccountStatus.ACTIVE,
                true
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isTrue();
    }

    @Test
    @DisplayName("Esattamente a 8 ore authenticatedAt deve invalidare")
    void shouldRejectExactlyAtAbsoluteTimeout() {
        evaluator = evaluatorWithClock(AUTHENTICATED_AT.plus(Duration.ofHours(8)));
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.CLIENT,
                AccountStatus.ACTIVE,
                true
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isFalse();
    }

    @Test
    @DisplayName("Dopo 8 ore deve invalidare")
    void shouldRejectAfterAbsoluteTimeout() {
        evaluator = evaluatorWithClock(AUTHENTICATED_AT.plus(Duration.ofHours(8)).plusMillis(1));
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.CLIENT,
                AccountStatus.ACTIVE,
                true
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isFalse();
    }

    @Test
    @DisplayName("authenticatedAt assente o non Instant deve invalidare")
    void shouldRejectMissingOrInvalidAuthenticatedAt() {
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.CLIENT,
                AccountStatus.ACTIVE,
                true
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, null)).isFalse();
        assertThat(evaluator.isAuthenticationStillValid(authentication, "2026-07-25T10:00:00Z")).isFalse();
        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT.toEpochMilli()))
                .isFalse();
    }

    @Test
    @DisplayName("Utente assente deve invalidare")
    void shouldRejectMissingUser() {
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.empty());

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isFalse();
    }

    @Test
    @DisplayName("Account non ACTIVE deve invalidare")
    void shouldRejectNonActiveAccount() {
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.CLIENT,
                AccountStatus.PENDING_VERIFICATION,
                true
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isFalse();
    }

    @Test
    @DisplayName("Email non verificata deve invalidare")
    void shouldRejectUnverifiedEmail() {
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.CLIENT,
                AccountStatus.ACTIVE,
                false
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isFalse();
    }

    @Test
    @DisplayName("Ruolo DB diverso dall'authority deve invalidare")
    void shouldRejectRoleMismatch() {
        Authentication authentication = authentication(7L, Role.CLIENT);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.PROFESSIONAL,
                AccountStatus.ACTIVE,
                true
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isFalse();
    }

    @Test
    @DisplayName("Non deve dipendere da profile.active: nessuna query oltre lo snapshot")
    void shouldNotDependOnProfileActive() {
        Authentication authentication = authentication(7L, Role.PROFESSIONAL);
        when(userRepository.findSecuritySnapshotById(7L)).thenReturn(Optional.of(snapshot(
                7L,
                Role.PROFESSIONAL,
                AccountStatus.ACTIVE,
                true
        )));

        assertThat(evaluator.isAuthenticationStillValid(authentication, AUTHENTICATED_AT)).isTrue();
        verify(userRepository).findSecuritySnapshotById(7L);
        verify(userRepository, never()).findById(anyLong());
        verify(userRepository, never()).findByEmail(any());
    }

    private SessionAuthenticationStateEvaluator evaluatorWithClock(Instant now) {
        ApplicationTimeProvider timeProvider = new ApplicationTimeProvider(
                Clock.fixed(now, ZoneOffset.UTC),
                new TimeProperties(ZoneOffset.ofHours(2), ZoneOffset.UTC)
        );
        return new SessionAuthenticationStateEvaluator(
                userRepository,
                timeProvider,
                new AuthenticatedUserIdResolver()
        );
    }

    private static Authentication authentication(Long userId, Role role) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(userId, "user" + userId + "@example.com"),
                null,
                List.of(new SimpleGrantedAuthority(role.name()))
        );
    }

    private static UserSecuritySnapshot snapshot(
            Long id,
            Role role,
            AccountStatus accountStatus,
            boolean emailVerified
    ) {
        return new UserSecuritySnapshot(id, role, accountStatus, emailVerified);
    }
}
