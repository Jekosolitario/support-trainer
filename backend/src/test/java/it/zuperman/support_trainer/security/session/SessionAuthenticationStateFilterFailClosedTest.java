package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.FilterChain;

class SessionAuthenticationStateFilterFailClosedTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Invalida HttpSession anche se SecurityContextRepository fallisce durante il cleanup")
    void mustInvalidateHttpSessionEvenWhenSecurityContextRepositoryFails() throws Exception {
        SessionAuthenticationStateEvaluator evaluator = mock(SessionAuthenticationStateEvaluator.class);
        when(evaluator.isAuthenticationStillValid(any(), any())).thenReturn(false);

        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        doThrow(new IllegalStateException("repository cleanup failed"))
                .when(securityContextRepository)
                .saveContext(any(), any(), any());

        AtomicBoolean entryPointCalled = new AtomicBoolean(false);
        AuthenticationEntryPoint entryPoint = (request, response, authException) -> {
            entryPointCalled.set(true);
            assertThat(authException).isInstanceOf(AuthenticationException.class);
            response.setStatus(401);
        };

        SessionAuthenticationStateFilter filter = new SessionAuthenticationStateFilter(
                evaluator,
                securityContextRepository,
                entryPoint
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributeNames.AUTHENTICATED_AT, java.time.Instant.parse("2026-01-01T00:00:00Z"));
        request.setSession(session);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUserPrincipal(1L, "user@example.com"),
                        null,
                        List.of(new SimpleGrantedAuthority("CLIENT"))
                )
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(entryPointCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(session.isInvalid()).isTrue();
        verify(chain, org.mockito.Mockito.never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("401 anche se HttpSession.invalidate lancia RuntimeException")
    void mustReturn401WhenInvalidateThrowsRuntimeException() throws Exception {
        SessionAuthenticationStateEvaluator evaluator = mock(SessionAuthenticationStateEvaluator.class);
        when(evaluator.isAuthenticationStillValid(any(), any())).thenReturn(false);

        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);

        AtomicBoolean entryPointCalled = new AtomicBoolean(false);
        AuthenticationEntryPoint entryPoint = (request, response, authException) -> {
            entryPointCalled.set(true);
            assertThat(authException).isInstanceOf(AuthenticationException.class);
            response.setStatus(401);
        };

        SessionAuthenticationStateFilter filter = new SessionAuthenticationStateFilter(
                evaluator,
                securityContextRepository,
                entryPoint
        );

        AtomicBoolean invalidateAttempted = new AtomicBoolean(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession() {
            @Override
            public void invalidate() {
                invalidateAttempted.set(true);
                throw new RuntimeException("jdbc invalidate failed");
            }
        };
        session.setAttribute(SessionAttributeNames.AUTHENTICATED_AT, java.time.Instant.parse("2026-01-01T00:00:00Z"));
        request.setSession(session);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUserPrincipal(2L, "user2@example.com"),
                        null,
                        List.of(new SimpleGrantedAuthority("CLIENT"))
                )
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(securityContextRepository, times(1)).saveContext(any(), any(), any());
        assertThat(invalidateAttempted).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(entryPointCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, org.mockito.Mockito.never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("401 anche se repository cleanup e invalidate falliscono entrambi")
    void mustReturn401WhenRepositoryCleanupAndInvalidateBothFail() throws Exception {
        SessionAuthenticationStateEvaluator evaluator = mock(SessionAuthenticationStateEvaluator.class);
        when(evaluator.isAuthenticationStillValid(any(), any())).thenReturn(false);

        SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
        doThrow(new IllegalStateException("repository cleanup failed"))
                .when(securityContextRepository)
                .saveContext(any(), any(), any());

        AtomicBoolean entryPointCalled = new AtomicBoolean(false);
        AuthenticationEntryPoint entryPoint = (request, response, authException) -> {
            entryPointCalled.set(true);
            response.setStatus(401);
        };

        SessionAuthenticationStateFilter filter = new SessionAuthenticationStateFilter(
                evaluator,
                securityContextRepository,
                entryPoint
        );

        AtomicBoolean invalidateAttempted = new AtomicBoolean(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession() {
            @Override
            public void invalidate() {
                invalidateAttempted.set(true);
                throw new RuntimeException("jdbc invalidate failed");
            }
        };
        session.setAttribute(SessionAttributeNames.AUTHENTICATED_AT, java.time.Instant.parse("2026-01-01T00:00:00Z"));
        request.setSession(session);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUserPrincipal(3L, "user3@example.com"),
                        null,
                        List.of(new SimpleGrantedAuthority("CLIENT"))
                )
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(securityContextRepository, times(1)).saveContext(any(), any(), any());
        assertThat(invalidateAttempted).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(entryPointCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, org.mockito.Mockito.never()).doFilter(any(), any());
    }
}
