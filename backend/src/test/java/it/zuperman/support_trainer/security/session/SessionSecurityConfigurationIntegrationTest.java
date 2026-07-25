package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SessionSecurityConfigurationIntegrationTest {

    @Autowired
    private SecurityContextRepository securityContextRepository;

    @Autowired
    private HttpSessionCsrfTokenRepository csrfTokenRepository;

    @Autowired
    private ChangeSessionIdAuthenticationStrategy changeSessionIdAuthenticationStrategy;

    @Autowired
    private CsrfAuthenticationStrategy csrfAuthenticationStrategy;

    @Autowired
    private SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Autowired
    private SessionAuthenticationStateFilter sessionAuthenticationStateFilter;

    @Autowired
    private FilterRegistrationBean<SessionAuthenticationStateFilter> sessionAuthenticationStateFilterRegistration;

    @Test
    @DisplayName("Spring beans devono possedere le fondazioni di sicurezza di sessione")
    void springBeansMustOwnSessionSecurityFoundations() {
        assertThat(securityContextRepository).isInstanceOf(DelegatingSecurityContextRepository.class);
        assertThat(csrfTokenRepository).isInstanceOf(HttpSessionCsrfTokenRepository.class);
        assertThat(changeSessionIdAuthenticationStrategy)
                .isInstanceOf(ChangeSessionIdAuthenticationStrategy.class);
        assertThat(csrfAuthenticationStrategy).isInstanceOf(CsrfAuthenticationStrategy.class);
        assertThat(sessionAuthenticationStrategy).isInstanceOf(CompositeSessionAuthenticationStrategy.class);
    }

    @Test
    @DisplayName("Composite deve ruotare session id e poi invalidare CSRF, con istanze condivise")
    void compositeMustOrderChangeSessionIdThenCsrfWithSharedInstances() throws Exception {
        List<SessionAuthenticationStrategy> delegates = compositeDelegates(sessionAuthenticationStrategy);

        assertThat(delegates).containsExactly(
                changeSessionIdAuthenticationStrategy,
                csrfAuthenticationStrategy
        );
        assertThat(delegates.get(0)).isSameAs(changeSessionIdAuthenticationStrategy);
        assertThat(delegates.get(1)).isSameAs(csrfAuthenticationStrategy);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        var generated = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(generated, request, response);
        assertThat(csrfTokenRepository.loadToken(request)).isNotNull();

        csrfAuthenticationStrategy.onAuthentication(authentication(), request, response);

        assertThat(csrfTokenRepository.loadToken(request)).isNull();
    }

    @Test
    @DisplayName("SessionSecurityFoundations non deve più esistere")
    void sessionSecurityFoundationsClassMustNoLongerExist() {
        assertThatThrownBy(() -> Class.forName(
                "it.zuperman.support_trainer.security.session.SessionSecurityFoundations"
        )).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("FilterRegistrationBean del state filter deve essere disabilitato")
    void sessionAuthenticationStateFilterRegistrationMustBeDisabled() {
        assertThat(sessionAuthenticationStateFilterRegistration.getFilter())
                .isSameAs(sessionAuthenticationStateFilter);
        assertThat(sessionAuthenticationStateFilterRegistration.isEnabled()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private static List<SessionAuthenticationStrategy> compositeDelegates(
            SessionAuthenticationStrategy strategy
    ) throws Exception {
        assertThat(strategy).isInstanceOf(CompositeSessionAuthenticationStrategy.class);
        Field field = CompositeSessionAuthenticationStrategy.class.getDeclaredField("delegateStrategies");
        field.setAccessible(true);
        return (List<SessionAuthenticationStrategy>) field.get(strategy);
    }

    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(1L, "user@example.com"),
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        );
    }
}
