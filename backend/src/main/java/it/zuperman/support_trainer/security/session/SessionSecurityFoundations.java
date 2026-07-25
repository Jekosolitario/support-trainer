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
 * Passive factory for the future session-security composition.
 * Intentionally not a Spring {@code @Configuration}/{@code @Bean}: registering these
 * components as beans could auto-wire them into the current JWT {@code SecurityFilterChain}.
 */
public final class SessionSecurityFoundations {

    private SessionSecurityFoundations() {
    }

    public static SessionSecurityComponents create() {
        HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();
        ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy
                = new ChangeSessionIdAuthenticationStrategy();
        CsrfAuthenticationStrategy csrfAuthenticationStrategy
                = new CsrfAuthenticationStrategy(csrfTokenRepository);
        SessionAuthenticationStrategy sessionAuthenticationStrategy
                = new CompositeSessionAuthenticationStrategy(List.of(
                        changeSessionIdAuthenticationStrategy,
                        csrfAuthenticationStrategy
                ));
        SecurityContextRepository securityContextRepository = new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()
        );
        return new SessionSecurityComponents(
                securityContextRepository,
                csrfTokenRepository,
                changeSessionIdAuthenticationStrategy,
                csrfAuthenticationStrategy,
                sessionAuthenticationStrategy
        );
    }

    public static final class SessionSecurityComponents {

        private final SecurityContextRepository securityContextRepository;
        private final HttpSessionCsrfTokenRepository csrfTokenRepository;
        private final ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy;
        private final CsrfAuthenticationStrategy csrfAuthenticationStrategy;
        private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

        private SessionSecurityComponents(
                SecurityContextRepository securityContextRepository,
                HttpSessionCsrfTokenRepository csrfTokenRepository,
                ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy,
                CsrfAuthenticationStrategy csrfAuthenticationStrategy,
                SessionAuthenticationStrategy sessionAuthenticationStrategy
        ) {
            this.securityContextRepository = Objects.requireNonNull(securityContextRepository);
            this.csrfTokenRepository = Objects.requireNonNull(csrfTokenRepository);
            this.changeSessionIdAuthenticationStrategy
                    = Objects.requireNonNull(changeSessionIdAuthenticationStrategy);
            this.csrfAuthenticationStrategy = Objects.requireNonNull(csrfAuthenticationStrategy);
            this.sessionAuthenticationStrategy = Objects.requireNonNull(sessionAuthenticationStrategy);
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
    }
}
