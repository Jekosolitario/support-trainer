package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

class AuthenticatedUserIdResolverTest {

    private final AuthenticatedUserIdResolver resolver = new AuthenticatedUserIdResolver();

    @Test
    @DisplayName("Deve risolvere l'ID dal principal tipizzato")
    void shouldResolveFromAuthenticatedUserPrincipal() {
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUserPrincipal(42L, "user@example.com"),
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        );

        assertThat(resolver.findUserId(authentication)).contains(42L);
        assertThat(resolver.requireUserId(authentication)).isEqualTo(42L);
    }

    @Test
    @DisplayName("Deve rifiutare autenticazioni assenti, non autenticate o non tipizzate")
    void shouldRejectMissingOrNonCanonicalAuthentication() {
        assertThat(resolver.findUserId(null)).isEmpty();

        var unauthenticated = new UsernamePasswordAuthenticationToken("anonymous", null);
        unauthenticated.setAuthenticated(false);
        assertThat(resolver.findUserId(unauthenticated)).isEmpty();

        var emailNamed = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        );
        assertThat(resolver.findUserId(emailNamed)).isEmpty();

        var numericStringPrincipal = new UsernamePasswordAuthenticationToken(
                "99",
                null,
                List.of(new SimpleGrantedAuthority("PROFESSIONAL"))
        );
        assertThat(resolver.findUserId(numericStringPrincipal)).isEmpty();

        var nullPrincipal = new UsernamePasswordAuthenticationToken(
                null,
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        );
        assertThat(resolver.findUserId(nullPrincipal)).isEmpty();

        var jwtStylePrincipal = new UsernamePasswordAuthenticationToken(
                User.withUsername("99")
                        .password("encoded-password")
                        .authorities("CLIENT")
                        .build(),
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        );
        assertThat(resolver.findUserId(jwtStylePrincipal)).isEmpty();

        var unexpectedPrincipalWithNumericName = new UsernamePasswordAuthenticationToken(
                new Object() {
                    @Override
                    public String toString() {
                        return "77";
                    }
                },
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        ) {
            @Override
            public String getName() {
                return "77";
            }
        };
        assertThat(resolver.findUserId(unexpectedPrincipalWithNumericName)).isEmpty();

        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );
        assertThat(anonymous.isAuthenticated()).isTrue();
        assertThat(resolver.findUserId(anonymous)).isEmpty();

        assertThatThrownBy(() -> resolver.requireUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical user id");
        assertThatThrownBy(() -> resolver.requireUserId(numericStringPrincipal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical user id");
    }
}
