package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.service.AuthService;
import it.zuperman.support_trainer.common.enums.Role;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;

class SessionLoginOrchestratorFailClosedTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Fail-closed quando fixation riesce ma il delegate CSRF successivo fallisce")
    void shouldFailClosedWhenLaterCompositeDelegateFailsAfterFixation() {
        AuthService authService = mock(AuthService.class);
        when(authService.authenticateForSession(any())).thenReturn(
                new SessionLoginIdentity(42L, "user@example.com", Role.CLIENT)
        );

        AtomicReference<String> sessionIdAfterFixation = new AtomicReference<>();
        SessionAuthenticationStrategy failingAfterFixation = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                (authentication, request, response) -> {
                    sessionIdAfterFixation.set(request.getSession(false).getId());
                    throw new IllegalStateException("csrf strategy failed");
                }
        ));

        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        ApplicationTimeProvider timeProvider = mock(ApplicationTimeProvider.class);

        SessionLoginOrchestrator orchestrator = new SessionLoginOrchestrator(
                authService,
                failingAfterFixation,
                securityContextRepository,
                timeProvider
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession originalSession = new MockHttpSession();
        request.setSession(originalSession);
        String originalSessionId = originalSession.getId();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> orchestrator.login(
                new LoginRequest("user@example.com", "Password123!"),
                request,
                response
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("csrf strategy failed");

        assertThat(sessionIdAfterFixation.get())
                .isNotBlank()
                .isNotEqualTo(originalSessionId);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(originalSession.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("Fail-closed quando saveContext fallisce dopo le session strategies")
    void shouldFailClosedWhenSaveContextFails() {
        AuthService authService = mock(AuthService.class);
        when(authService.authenticateForSession(any())).thenReturn(
                new SessionLoginIdentity(7L, "pro@example.com", Role.PROFESSIONAL)
        );

        SessionAuthenticationStrategy strategy = new ChangeSessionIdAuthenticationStrategy();
        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        doThrow(new IllegalStateException("saveContext failed"))
                .when(securityContextRepository)
                .saveContext(any(), any(), any());

        SessionLoginOrchestrator orchestrator = new SessionLoginOrchestrator(
                authService,
                strategy,
                securityContextRepository,
                mock(ApplicationTimeProvider.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> orchestrator.login(
                new LoginRequest("pro@example.com", "Password123!"),
                request,
                response
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("saveContext failed");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("Fail-closed quando HttpSession.setAttribute(authenticatedAt) fallisce")
    void shouldFailClosedWhenAuthenticatedAtWriteFails() {
        AuthService authService = mock(AuthService.class);
        when(authService.authenticateForSession(any())).thenReturn(
                new SessionLoginIdentity(9L, "write@example.com", Role.CLIENT)
        );

        SessionAuthenticationStrategy strategy = mock(SessionAuthenticationStrategy.class);
        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        ApplicationTimeProvider timeProvider = mock(ApplicationTimeProvider.class);
        when(timeProvider.nowInstant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));

        SessionLoginOrchestrator orchestrator = new SessionLoginOrchestrator(
                authService,
                strategy,
                securityContextRepository,
                timeProvider
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession() {
            @Override
            public void setAttribute(String name, Object value) {
                if (SessionAttributeNames.AUTHENTICATED_AT.equals(name)) {
                    throw new IllegalStateException("authenticatedAt write failed");
                }
                super.setAttribute(name, value);
            }
        };
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> orchestrator.login(
                new LoginRequest("write@example.com", "Password123!"),
                request,
                response
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("authenticatedAt write failed");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("Cleanup invalida la sessione anche se saveContext del rollback fallisce")
    void cleanupMustInvalidateSessionEvenWhenRollbackSaveContextFails() {
        AuthService authService = mock(AuthService.class);
        when(authService.authenticateForSession(any())).thenReturn(
                new SessionLoginIdentity(11L, "rollback@example.com", Role.CLIENT)
        );

        AtomicBoolean strategyInvoked = new AtomicBoolean(false);
        SessionAuthenticationStrategy strategy = (Authentication authentication,
                jakarta.servlet.http.HttpServletRequest request,
                jakarta.servlet.http.HttpServletResponse response) -> {
            strategyInvoked.set(true);
            throw new IllegalStateException("strategy failed");
        };

        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("rollback save failed");
        }).when(securityContextRepository).saveContext(any(), any(), any());

        SessionLoginOrchestrator orchestrator = new SessionLoginOrchestrator(
                authService,
                strategy,
                securityContextRepository,
                mock(ApplicationTimeProvider.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> orchestrator.login(
                new LoginRequest("rollback@example.com", "Password123!"),
                request,
                response
        ));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("strategy failed");
        assertThat(thrown.getSuppressed())
                .anyMatch(ex -> ex instanceof IllegalStateException
                        && "rollback save failed".equals(ex.getMessage()));
        assertThat(strategyInvoked).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("Invalidate con RuntimeException non maschera l'eccezione originale")
    void invalidateRuntimeExceptionMustNotMaskOriginalFailure() {
        AuthService authService = mock(AuthService.class);
        when(authService.authenticateForSession(any())).thenReturn(
                new SessionLoginIdentity(13L, "invalidate@example.com", Role.CLIENT)
        );

        SessionAuthenticationStrategy strategy = (authentication, request, response) -> {
            throw new IllegalStateException("strategy failed");
        };

        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        AtomicBoolean invalidateAttempted = new AtomicBoolean(false);
        RuntimeException invalidateFailure = new RuntimeException("jdbc invalidate failed");

        SessionLoginOrchestrator orchestrator = new SessionLoginOrchestrator(
                authService,
                strategy,
                securityContextRepository,
                mock(ApplicationTimeProvider.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession() {
            @Override
            public void invalidate() {
                invalidateAttempted.set(true);
                throw invalidateFailure;
            }
        };
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> orchestrator.login(
                new LoginRequest("invalidate@example.com", "Password123!"),
                request,
                response
        ));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("strategy failed");
        assertThat(thrown.getSuppressed()).contains(invalidateFailure);
        assertThat(invalidateAttempted).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // invalidate failed before marking invalid: same session remains, no new one created
        assertThat(request.getSession(false)).isSameAs(session);
    }

    @Test
    @DisplayName("Repository cleanup e invalidate falliscono: originale resta primaria")
    void repositoryAndInvalidateFailuresKeepOriginalPrimary() {
        AuthService authService = mock(AuthService.class);
        when(authService.authenticateForSession(any())).thenReturn(
                new SessionLoginIdentity(15L, "both@example.com", Role.CLIENT)
        );

        SessionAuthenticationStrategy strategy = (authentication, request, response) -> {
            throw new IllegalStateException("strategy failed");
        };

        IllegalStateException repositoryFailure = new IllegalStateException("rollback save failed");
        RuntimeException invalidateFailure = new RuntimeException("jdbc invalidate failed");
        AtomicBoolean invalidateAttempted = new AtomicBoolean(false);

        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        doThrow(repositoryFailure).when(securityContextRepository).saveContext(any(), any(), any());

        SessionLoginOrchestrator orchestrator = new SessionLoginOrchestrator(
                authService,
                strategy,
                securityContextRepository,
                mock(ApplicationTimeProvider.class)
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession() {
            @Override
            public void invalidate() {
                invalidateAttempted.set(true);
                throw invalidateFailure;
            }
        };
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Throwable thrown = catchThrowable(() -> orchestrator.login(
                new LoginRequest("both@example.com", "Password123!"),
                request,
                response
        ));

        assertThat(thrown).isInstanceOf(IllegalStateException.class)
                .hasMessage("strategy failed");
        verify(securityContextRepository, times(1)).saveContext(any(), any(), any());
        assertThat(invalidateAttempted).isTrue();
        assertThat(thrown.getSuppressed()).contains(repositoryFailure, invalidateFailure);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
