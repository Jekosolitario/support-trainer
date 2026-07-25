package it.zuperman.support_trainer.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticatedUserPrincipalTest {

    @Test
    @DisplayName("Deve costruire un principal minimo valido")
    void shouldBuildValidPrincipal() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(42L, "user@example.com");

        assertThat(principal.getUserId()).isEqualTo(42L);
        assertThat(principal.getEmail()).isEqualTo("user@example.com");
        assertThat(principal.getName()).isEqualTo("42");
    }

    @Test
    @DisplayName("Deve rifiutare userId o email null/blank")
    void shouldRejectNullIdentityFields() {
        assertThatThrownBy(() -> new AuthenticatedUserPrincipal(null, "user@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        assertThatThrownBy(() -> new AuthenticatedUserPrincipal(1L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
        assertThatThrownBy(() -> new AuthenticatedUserPrincipal(1L, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("Equality e hashCode devono usare soltanto l'ID canonico")
    void equalityMustUseCanonicalUserIdOnly() {
        AuthenticatedUserPrincipal first = new AuthenticatedUserPrincipal(7L, "a@example.com");
        AuthenticatedUserPrincipal second = new AuthenticatedUserPrincipal(7L, "b@example.com");
        AuthenticatedUserPrincipal third = new AuthenticatedUserPrincipal(8L, "a@example.com");

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(third);
    }

    @Test
    @DisplayName("I soli campi di istanza ammessi devono essere userId ed email")
    void shouldExposeOnlyCanonicalInstanceFields() {
        Field[] instanceFields = Arrays.stream(AuthenticatedUserPrincipal.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toArray(Field[]::new);

        assertThat(instanceFields)
                .extracting(Field::getName)
                .containsExactlyInAnyOrder("userId", "email");
        assertThat(instanceFields).allMatch(field -> Modifier.isFinal(field.getModifiers()));
    }

    @Test
    @DisplayName("Deve dichiarare serialVersionUID esplicito e serializzare soltanto id ed email")
    void shouldDeclareSerialVersionUidAndSerializeMinimalGraph() throws Exception {
        Field serialVersionUid = AuthenticatedUserPrincipal.class.getDeclaredField("serialVersionUID");
        serialVersionUid.setAccessible(true);
        assertThat(serialVersionUid.getLong(null)).isEqualTo(1L);
        assertThat(ObjectStreamClass.lookup(AuthenticatedUserPrincipal.class).getSerialVersionUID())
                .isEqualTo(1L);

        AuthenticatedUserPrincipal original = new AuthenticatedUserPrincipal(99L, "serial@example.com");
        AuthenticatedUserPrincipal restored = roundTrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.getEmail()).isEqualTo("serial@example.com");
        assertThat(restored.getName()).isEqualTo("99");

        byte[] bytes = serialize(original);
        String payload = new String(bytes);
        assertThat(payload).doesNotContain("password");
        assertThat(payload).doesNotContain("PROFESSIONAL");
        assertThat(payload).doesNotContain("specialization");
        assertThat(payload).doesNotContain("ClientProfile");
    }

    private static AuthenticatedUserPrincipal roundTrip(AuthenticatedUserPrincipal principal) throws Exception {
        return deserialize(serialize(principal), AuthenticatedUserPrincipal.class);
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
