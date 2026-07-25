package it.zuperman.support_trainer.security.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import it.zuperman.support_trainer.security.session.SessionAuthenticationStateEvaluator;
import it.zuperman.support_trainer.security.session.SessionAuthenticationStateFilter;

@Configuration
public class SessionSecurityConfiguration {

    @Bean
    CookieSerializer cookieSerializer(
            @Value("${server.servlet.session.cookie.name:STSESSION}") String cookieName,
            @Value("${server.servlet.session.cookie.path:/}") String cookiePath,
            @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:strict}") String sameSite
    ) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookiePath(cookiePath);
        serializer.setUseHttpOnlyCookie(httpOnly);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite(sameSite);
        return serializer;
    }

    @Bean
    HttpSessionCsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    CsrfAuthenticationStrategy csrfAuthenticationStrategy(HttpSessionCsrfTokenRepository csrfTokenRepository) {
        return new CsrfAuthenticationStrategy(csrfTokenRepository);
    }

    /**
     * Ordered composite: session-id rotation first, then CSRF token invalidation.
     */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy,
            CsrfAuthenticationStrategy csrfAuthenticationStrategy
    ) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                changeSessionIdAuthenticationStrategy,
                csrfAuthenticationStrategy
        ));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository()
        );
    }

    @Bean
    SessionAuthenticationStateFilter sessionAuthenticationStateFilter(
            SessionAuthenticationStateEvaluator sessionAuthenticationStateEvaluator,
            SecurityContextRepository securityContextRepository,
            AuthenticationEntryPoint restAuthenticationEntryPoint
    ) {
        return new SessionAuthenticationStateFilter(
                sessionAuthenticationStateEvaluator,
                securityContextRepository,
                restAuthenticationEntryPoint
        );
    }

    /**
     * Prevents Spring Boot from registering the security filter as a standalone servlet filter.
     */
    @Bean
    FilterRegistrationBean<SessionAuthenticationStateFilter> sessionAuthenticationStateFilterRegistration(
            SessionAuthenticationStateFilter sessionAuthenticationStateFilter
    ) {
        FilterRegistrationBean<SessionAuthenticationStateFilter> registration
                = new FilterRegistrationBean<>(sessionAuthenticationStateFilter);
        registration.setEnabled(false);
        return registration;
    }
}
