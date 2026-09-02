package it.zuperman.support_trainer.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Role;

class AuthenticationUserDetailsTest {

    private static final String BCRYPT_HASH = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012345";

    @Test
    @DisplayName("Lo snapshot espone userId, role, sessionVersion e hash dallo stesso costruttore")
    void shouldExposeAuthenticationSnapshotFields() {
        AuthenticationUserDetails snapshot = snapshot(3L, 7L);

        assertThat(snapshot.getUserId()).isEqualTo(3L);
        assertThat(snapshot.getEmail()).isEqualTo("snap@example.com");
        assertThat(snapshot.getUsername()).isEqualTo("snap@example.com");
        assertThat(snapshot.getPassword()).isEqualTo(BCRYPT_HASH);
        assertThat(snapshot.getRole()).isEqualTo(Role.PROFESSIONAL);
        assertThat(snapshot.getSessionVersion()).isEqualTo(7L);
        assertThat(snapshot.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(snapshot.getEmailVerified()).isTrue();
        assertThat(snapshot.getAuthorities()).extracting("authority").containsExactly("PROFESSIONAL");
    }

    @Test
    @DisplayName("eraseCredentials deve azzerare solo l'hash e lasciare sessionVersion")
    void eraseCredentialsMustClearHashOnly() {
        AuthenticationUserDetails snapshot = snapshot(4L, 2L);

        snapshot.eraseCredentials();

        assertThat(snapshot.getPassword()).isNull();
        assertThat(snapshot.getSessionVersion()).isEqualTo(2L);
        assertThat(snapshot.getUserId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("toString non deve contenere hash o email; la serializzazione non deve contenere l'hash")
    void toStringAndSerializationMustOmitSecrets() throws Exception {
        AuthenticationUserDetails snapshot = snapshot(9L, 1L);

        assertThat(snapshot.toString())
                .isEqualTo("AuthenticationUserDetails[userId=9, sessionVersion=1]")
                .doesNotContain(BCRYPT_HASH)
                .doesNotContain("snap@example.com")
                .doesNotContain("password");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(snapshot);
        }
        String payload = new String(buffer.toByteArray());
        assertThat(payload).doesNotContain(BCRYPT_HASH);
    }

    @Test
    @DisplayName("Deve rifiutare identità o hash assenti")
    void shouldRejectMissingIdentityOrHash() {
        assertThatThrownBy(() -> new AuthenticationUserDetails(
                null, "a@example.com", BCRYPT_HASH, Role.CLIENT, 0L, AccountStatus.ACTIVE, true
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationUserDetails(
                1L, " ", BCRYPT_HASH, Role.CLIENT, 0L, AccountStatus.ACTIVE, true
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthenticationUserDetails(
                1L, "a@example.com", " ", Role.CLIENT, 0L, AccountStatus.ACTIVE, true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static AuthenticationUserDetails snapshot(Long userId, long sessionVersion) {
        return new AuthenticationUserDetails(
                userId,
                "snap@example.com",
                BCRYPT_HASH,
                Role.PROFESSIONAL,
                sessionVersion,
                AccountStatus.ACTIVE,
                true
        );
    }
}
