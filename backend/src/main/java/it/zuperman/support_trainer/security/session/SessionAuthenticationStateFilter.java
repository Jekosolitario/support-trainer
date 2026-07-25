package it.zuperman.support_trainer.security.session;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Validates session authentication readiness before authorization.
 * Registered only in the Spring Security filter chain (servlet registration disabled).
 */
public final class SessionAuthenticationStateFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticationStateFilter.class);

    private final SessionAuthenticationStateEvaluator sessionAuthenticationStateEvaluator;
    private final SecurityContextRepository securityContextRepository;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public SessionAuthenticationStateFilter(
            SessionAuthenticationStateEvaluator sessionAuthenticationStateEvaluator,
            SecurityContextRepository securityContextRepository,
            AuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.sessionAuthenticationStateEvaluator = sessionAuthenticationStateEvaluator;
        this.securityContextRepository = securityContextRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Object authenticatedAt = session == null
                ? null
                : session.getAttribute(SessionAttributeNames.AUTHENTICATED_AT);

        if (sessionAuthenticationStateEvaluator.isAuthenticationStillValid(authentication, authenticatedAt)) {
            filterChain.doFilter(request, response);
            return;
        }

        invalidateSessionAuthentication(request, response);
        authenticationEntryPoint.commence(
                request,
                response,
                new AuthenticationException("Session authentication is no longer valid") {
                }
        );
    }

    /**
     * Clears authentication state after the evaluator rejects the session.
     * Repository cleanup and session invalidation are isolated; infrastructural
     * failures are logged and must not prevent the subsequent 401 entry point.
     * Never creates a session ({@code getSession(false)} only).
     */
    private void invalidateSessionAuthentication(HttpServletRequest request, HttpServletResponse response) {
        try {
            SecurityContextHolder.clearContext();
        } catch (RuntimeException cleanupEx) {
            log.warn("Failed to clear SecurityContextHolder during session auth invalidation", cleanupEx);
        }

        try {
            SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
            securityContextRepository.saveContext(emptyContext, request, response);
        } catch (RuntimeException cleanupEx) {
            log.warn("Failed to clear SecurityContextRepository during session auth invalidation", cleanupEx);
        }

        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        } catch (RuntimeException cleanupEx) {
            log.warn("Failed to invalidate HttpSession during session auth invalidation", cleanupEx);
        }
    }
}
