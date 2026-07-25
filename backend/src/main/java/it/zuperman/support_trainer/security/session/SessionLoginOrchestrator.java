package it.zuperman.support_trainer.security.session;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import it.zuperman.support_trainer.auth.dto.request.LoginRequest;
import it.zuperman.support_trainer.auth.service.AuthService;
import it.zuperman.support_trainer.common.time.ApplicationTimeProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Service
public class SessionLoginOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SessionLoginOrchestrator.class);

    private final AuthService authService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final ApplicationTimeProvider timeProvider;

    public SessionLoginOrchestrator(
            AuthService authService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository,
            ApplicationTimeProvider timeProvider
    ) {
        this.authService = authService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.timeProvider = timeProvider;
    }

    public void login(
            LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        SessionLoginIdentity identity = authService.authenticateForSession(loginRequest);

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                identity.userId(),
                identity.email()
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(identity.role().name()))
        );

        boolean sessionAuthStarted = false;
        try {
            sessionAuthStarted = true;
            sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            HttpSession session = request.getSession(false);
            if (session == null) {
                throw new IllegalStateException("Authenticated session was not created");
            }
            session.setAttribute(SessionAttributeNames.AUTHENTICATED_AT, timeProvider.nowInstant());
        } catch (RuntimeException ex) {
            if (sessionAuthStarted) {
                failClosed(request, response, ex);
            }
            throw ex;
        }
    }

    /**
     * Fail-closed cleanup from the moment session authentication strategies start.
     * Preserves {@code original} as the primary failure; cleanup steps are isolated,
     * never create a session, and secondary failures are suppressed or logged.
     */
    private void failClosed(
            HttpServletRequest request,
            HttpServletResponse response,
            RuntimeException original
    ) {
        try {
            SecurityContextHolder.clearContext();
        } catch (RuntimeException cleanupEx) {
            attachSuppressedOrLog(original, cleanupEx, "SecurityContextHolder.clearContext");
        }

        try {
            SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
            securityContextRepository.saveContext(emptyContext, request, response);
        } catch (RuntimeException cleanupEx) {
            attachSuppressedOrLog(original, cleanupEx, "SecurityContextRepository.saveContext");
        }

        try {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        } catch (RuntimeException cleanupEx) {
            attachSuppressedOrLog(original, cleanupEx, "HttpSession.invalidate");
        }
    }

    private static void attachSuppressedOrLog(
            RuntimeException original,
            RuntimeException cleanupEx,
            String step
    ) {
        try {
            original.addSuppressed(cleanupEx);
        } catch (Exception associationFailure) {
            log.warn(
                    "Session login fail-closed cleanup failed at {} and could not be attached to the original failure",
                    step,
                    cleanupEx
            );
        }
    }
}
