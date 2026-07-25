package it.zuperman.support_trainer.security.session;

import java.util.List;
import java.util.Objects;

import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Passive provisional composition for the future session-security model.
 * Intentionally not a Spring {@code @Configuration}/{@code @Bean}: registering these
 * components as beans could auto-wire them into the current JWT {@code SecurityFilterChain}.
 * {@link #create()} returns one shared immutable composition so future consumers can share
 * the same instances; Lot 3 must own and wire those instances through Spring, not treat this
 * type as a service locator.
 */
public final class SessionSecurityFoundations {

    private static final SessionSecurityComponents COMPOSITION = compose();

    private SessionSecurityFoundations() {
    }

    public static SessionSecurityComponents create() {
        return COMPOSITION;
    }

    private static SessionSecurityComponents compose() {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy
                = new ChangeSessionIdAuthenticationStrategy();
        CsrfAuthenticationStrategy csrfAuthenticationStrategy
                = new CsrfAuthenticationStrategy(csrfTokenRepository);
        List<SessionAuthenticationStrategy> sessionAuthenticationStrategyDelegates = List.of(
                changeSessionIdAuthenticationStrategy,
                csrfAuthenticationStrategy
        );
        SessionAuthenticationStrategy sessionAuthenticationStrategy
                = new CompositeSessionAuthenticationStrategy(sessionAuthenticationStrategyDelegates);
        RequestAttributeSecurityContextRepository requestAttributeSecurityContextRepository
                = new RequestAttributeSecurityContextRepository();
        HttpSessionSecurityContextRepository httpSessionSecurityContextRepository
                = new HttpSessionSecurityContextRepository();
        List<SecurityContextRepository> securityContextRepositoryDelegates = List.of(
                requestAttributeSecurityContextRepository,
                httpSessionSecurityContextRepository
        );
        SecurityContextRepository securityContextRepository = new DelegatingSecurityContextRepository(
                requestAttributeSecurityContextRepository,
                httpSessionSecurityContextRepository
        );
        return new SessionSecurityComponents(
                securityContextRepository,
                securityContextRepositoryDelegates,
                requestAttributeSecurityContextRepository,
                httpSessionSecurityContextRepository,
                csrfTokenRepository,
                changeSessionIdAuthenticationStrategy,
                csrfAuthenticationStrategy,
                sessionAuthenticationStrategy,
                sessionAuthenticationStrategyDelegates
        );
    }

    public static final class SessionSecurityComponents {

        private final SecurityContextRepository securityContextRepository;
        private final List<SecurityContextRepository> securityContextRepositoryDelegates;
        private final RequestAttributeSecurityContextRepository requestAttributeSecurityContextRepository;
        private final HttpSessionSecurityContextRepository httpSessionSecurityContextRepository;
        private final HttpSessionCsrfTokenRepository csrfTokenRepository;
        private final ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy;
        private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;
        private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
        private final List<SessionAuthenticationStrategy> sessionAuthenticationStrategyDelegates;

        private SessionSecurityComponents(
                SecurityContextRepository securityContextRepository,
                List<SecurityContextRepository> securityContextRepositoryDelegates,
                RequestAttributeSecurityContextRepository requestAttributeSecurityContextRepository,
                HttpSessionSecurityContextRepository httpSessionSecurityContextRepository,
                HttpSessionCsrfTokenRepository csrfTokenRepository,
                ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy,
                CsrfAuthenticationStrategy csrfAuthenticationStrategy,
                SessionAuthenticationStrategy sessionAuthenticationStrategy,
                List<SessionAuthenticationStrategy> sessionAuthenticationStrategyDelegates
        ) {
            this.securityContextRepository = Objects.requireNonNull(securityContextRepository);
            this.securityContextRepositoryDelegates
                    = List.copyOf(Objects.requireNonNull(securityContextRepositoryDelegates));
            this.requestAttributeSecurityContextRepository
                    = Objects.requireNonNull(requestAttributeSecurityContextRepository);
            this.httpSessionSecurityContextRepository
                    = Objects.requireNonNull(httpSessionSecurityContextRepository);
            this.csrfTokenRepository = Objects.requireNonNull(csrfTokenRepository);
            this.changeSessionIdAuthenticationStrategy
                    = Objects.requireNonNull(changeSessionIdAuthenticationStrategy);
            this.csrfAuthenticationStrategy = Objects.requireNonNull(csrfAuthenticationStrategy);
            this.sessionAuthenticationStrategy = Objects.requireNonNull(sessionAuthenticationStrategy);
            this.sessionAuthenticationStrategyDelegates
                    = List.copyOf(Objects.requireNonNull(sessionAuthenticationStrategyDelegates));
        }

        public SecurityContextRepository securityContextRepository() {
            return securityContextRepository;
        }

        public HttpSessionCsrfTokenRepository csrfTokenRepository() {
            return csrfTokenRepository;
        }

        public ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy() {
            return changeSessionIdAuthenticationStrategy;
        }

        public CsrfAuthenticationStrategy csrfAuthenticationStrategy() {
            return csrfAuthenticationStrategy;
        }

        /**
         * Ordered composite: session-id rotation first, then CSRF token invalidation.
         */
        public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
            return sessionAuthenticationStrategy;
        }

        List<SecurityContextRepository> securityContextRepositoryDelegates() {
            return securityContextRepositoryDelegates;
        }

        RequestAttributeSecurityContextRepository requestAttributeSecurityContextRepository() {
            return requestAttributeSecurityContextRepository;
        }

        HttpSessionSecurityContextRepository httpSessionSecurityContextRepository() {
            return httpSessionSecurityContextRepository;
        }

        List<SessionAuthenticationStrategy> sessionAuthenticationStrategyDelegates() {
            return sessionAuthenticationStrategyDelegates;
        }
    }
}
