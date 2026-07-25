package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
    @DisplayName("Deve risolvere l'ID da Authentication.getName() quando canonico")
    void shouldResolveFromAuthenticationName() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "99",
                null,
                List.of(new SimpleGrantedAuthority("PROFESSIONAL"))
        );

        assertThat(resolver.findUserId(authentication)).contains(99L);
    }

    @Test
    @DisplayName("Deve rifiutare autenticazioni assenti o non canoniche")
    void shouldRejectMissingOrNonCanonicalAuthentication() {
        assertThat(resolver.findUserId(null)).isEmpty();

        var emailNamed = new UsernamePasswordAuthenticationToken(
                "user@example.com",
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        );
        assertThat(resolver.findUserId(emailNamed)).isEmpty();

        var anonymous = new UsernamePasswordAuthenticationToken("anonymous", null);
        anonymous.setAuthenticated(false);
        assertThat(resolver.findUserId(anonymous)).isEmpty();

        assertThatThrownBy(() -> resolver.requireUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical user id");
    }
}