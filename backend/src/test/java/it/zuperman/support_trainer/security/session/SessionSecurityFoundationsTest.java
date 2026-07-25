package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
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

        DelegatingSecurityContextRepository repository
                = (DelegatingSecurityContextRepository) components.securityContextRepository();
        assertThat(repository).extracting(Object::getClass).isEqualTo(DelegatingSecurityContextRepository.class);

        assertThat(components.securityContextRepository())
                .isNotInstanceOf(RequestAttributeSecurityContextRepository.class)
                .isNotInstanceOf(HttpSessionSecurityContextRepository.class);
    }

    @Test
    @DisplayName("La strategia composta deve invocare fixation e poi CSRF una sola volta ciascuno")
    void compositeStrategyMustInvokeFixationThenCsrfOnceEach() throws Exception {
        SessionAuthenticationStrategy changeSessionId = mock(SessionAuthenticationStrategy.class);
        SessionAuthenticationStrategy csrf = mock(SessionAuthenticationStrategy.class);
        SessionAuthenticationStrategy composite
                = new CompositeSessionAuthenticationStrategy(List.of(changeSessionId, csrf));

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(1L, "user@example.com"),
                null,
                List.of(new SimpleGrantedAuthority("PROFESSIONAL"))
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        composite.onAuthentication(authentication, request, response);

        InOrder order = inOrder(changeSessionId, csrf);
        order.verify(changeSessionId).onAuthentication(eq(authentication), eq(request), eq(response));
        order.verify(csrf).onAuthentication(eq(authentication), eq(request), eq(response));
        verify(changeSessionId, times(1)).onAuthentication(any(), any(), any());
        verify(csrf, times(1)).onAuthentication(any(), any(), any());
    }

    @Test
    @DisplayName("Le fondazioni passive non devono toccare request/response finché non invocate")
    void passiveFoundationsMustNotTouchHttpUntilInvoked() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        SessionSecurityFoundations.create();

        verifyNoInteractions(request, response);
    }
}
