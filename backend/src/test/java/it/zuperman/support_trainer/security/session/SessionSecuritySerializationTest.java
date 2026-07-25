package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

class SessionSecuritySerializationTest {

    @Test
    @DisplayName("Authentication e SecurityContext futuri devono serializzare principal minimo senza password")
    void shouldRoundTripFutureAuthenticationWithoutPassword() throws Exception {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(15L, "session@example.com");
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("CLIENT"))
        );
        SecurityContext securityContext = new SecurityContextImpl(authentication);

        SecurityContext restoredContext = roundTrip(securityContext);
        Authentication restoredAuthentication = restoredContext.getAuthentication();

        assertThat(restoredAuthentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(restoredAuthentication.getCredentials()).isNull();
        assertThat(restoredAuthentication.getPrincipal()).isInstanceOf(AuthenticatedUserPrincipal.class);
        AuthenticatedUserPrincipal restoredPrincipal
                = (AuthenticatedUserPrincipal) restoredAuthentication.getPrincipal();
        assertThat(restoredPrincipal.getUserId()).isEqualTo(15L);
        assertThat(restoredPrincipal.getEmail()).isEqualTo("session@example.com");
        assertThat(restoredPrincipal.getName()).isEqualTo("15");
        assertThat(restoredAuthentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("CLIENT");

        String payload = new String(serialize(securityContext));
        assertThat(payload).doesNotContain("secret-password");
        assertThat(payload).doesNotContain("password=");
    }

    private static SecurityContext roundTrip(SecurityContext context) throws Exception {
        return deserialize(serialize(context), SecurityContext.class);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
        }
        return buffer.toByteArray();
    }

    private static <T> T deserialize(byte[] bytes, Class<T> type) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return type.cast(input.readObject());
        }
    }
}
