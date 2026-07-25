package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

class SessionSecurityFoundationsTest {

    @Test
    @DisplayName("Deve comporre SecurityContextRepository e strategie CSRF senza bean Spring")
    void shouldComposePassiveSessionSecurityFoundations() {
        SessionSecurityFoundations.SessionSecurityComponents components = SessionSecurityFoundations.create();

        assertThat(components.securityContextRepository()).isInstanceOf(DelegatingSecurityContextRepository.class);
        assertThat(components.csrfTokenRepository()).isInstanceOf(HttpSessionCsrfTokenRepository.class);
        assertThat(components.changeSessionIdAuthenticationStrategy())
                .isInstanceOf(ChangeSessionIdAuthenticationStrategy.class);
        assertThat(components.csrfAuthenticationStrategy()).isInstanceOf(CsrfAuthenticationStrategy.class);
        assertThat(components.sessionAuthenticationStrategy())
                .isInstanceOf(CompositeSessionAuthenticationStrategy.class);

        assertThat(components.requestAttributeSecurityContextRepository())
                .isInstanceOf(RequestAttributeSecurityContextRepository.class);
        assertThat(components.httpSessionSecurityContextRepository())
                .isInstanceOf(HttpSessionSecurityContextRepository.class);
        assertThat(components.securityContextRepositoryDelegates()).containsExactly(
                components.requestAttributeSecurityContextRepository(),
                components.httpSessionSecurityContextRepository()
        );
    }

    @Test
    @DisplayName("create() deve restituire sempre la stessa composizione condivisa")
    void createMustReturnSameCompositionInstance() {
        SessionSecurityFoundations.SessionSecurityComponents first = SessionSecurityFoundations.create();
        SessionSecurityFoundations.SessionSecurityComponents second = SessionSecurityFoundations.create();

        assertThat(first).isSameAs(second);
        assertThat(first.securityContextRepository()).isSameAs(second.securityContextRepository());
        assertThat(first.csrfTokenRepository()).isSameAs(second.csrfTokenRepository());
        assertThat(first.sessionAuthenticationStrategy()).isSameAs(second.sessionAuthenticationStrategy());
        assertThat(first.changeSessionIdAuthenticationStrategy())
                .isSameAs(second.changeSessionIdAuthenticationStrategy());
        assertThat(first.csrfAuthenticationStrategy()).isSameAs(second.csrfAuthenticationStrategy());
    }

    @Test
    @DisplayName("La CSRF strategy reale deve usare la stessa HttpSessionCsrfTokenRepository")
    void csrfStrategyMustShareCsrfTokenRepositoryInstance() throws Exception {
        SessionSecurityFoundations.SessionSecurityComponents components = SessionSecurityFoundations.create();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(components.sessionAuthenticationStrategyDelegates()).containsExactly(
                components.changeSessionIdAuthenticationStrategy(),
                components.csrfAuthenticationStrategy()
        );
        assertThat(components.sessionAuthenticationStrategyDelegates().get(1))
                .isSameAs(components.csrfAuthenticationStrategy());

        var generated = components.csrfTokenRepository().generateToken(request);
        components.csrfTokenRepository().saveToken(generated, request, response);
        assertThat(components.csrfTokenRepository().loadToken(request)).isNotNull();

        components.csrfAuthenticationStrategy().onAuthentication(
                authentication(1L, "CLIENT"),
                request,
                response
        );

        assertThat(components.csrfTokenRepository().loadToken(request)).isNull();
    }

    @Test
    @DisplayName("La strategia composta reale deve delegare fixation e poi CSRF")
    void sessionAuthenticationStrategyMustDelegateInFixationThenCsrfOrder() {
        SessionSecurityFoundations.SessionSecurityComponents components = SessionSecurityFoundations.create();

        assertThat(components.sessionAuthenticationStrategy())
                .isInstanceOf(CompositeSessionAuthenticationStrategy.class);
        assertThat(components.sessionAuthenticationStrategyDelegates()).containsExactly(
                components.changeSessionIdAuthenticationStrategy(),
                components.csrfAuthenticationStrategy()
        );
    }

    @Test
    @DisplayName("Il DelegatingSecurityContextRepository deve preferire il context request-scoped")
    void securityContextRepositoryMustPreferRequestAttributeOnLoad() {
        SessionSecurityFoundations.SessionSecurityComponents components = SessionSecurityFoundations.create();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityContext requestContext = new SecurityContextImpl(authentication(1L, "CLIENT"));
        SecurityContext sessionContext = new SecurityContextImpl(authentication(2L, "PROFESSIONAL"));

        components.requestAttributeSecurityContextRepository().saveContext(requestContext, request, response);
        components.httpSessionSecurityContextRepository().saveContext(sessionContext, request, response);

        SecurityContext loaded = components.securityContextRepository()
                .loadDeferredContext(request)
                .get();

        assertThat(loaded.getAuthentication().getPrincipal())
                .isInstanceOf(AuthenticatedUserPrincipal.class);
        assertThat(((AuthenticatedUserPrincipal) loaded.getAuthentication().getPrincipal()).getUserId())
                .isEqualTo(1L);
        assertThat(loaded.getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("CLIENT");
    }

    @Test
    @DisplayName("Le fondazioni passive non devono toccare request/response finché non invocate")
    void passiveFoundationsMustNotTouchHttpUntilInvoked() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionSecurityFoundations.create();

        verifyNoInteractions(request, response);
    }

    private static Authentication authentication(Long userId, String role) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(userId, "user" + userId + "@example.com"),
                null,
                List.of(new SimpleGrantedAuthority(role))
        );
    }
}
