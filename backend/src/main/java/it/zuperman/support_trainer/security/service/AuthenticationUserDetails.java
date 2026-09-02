package it.zuperman.support_trainer.security.service;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Role;

/**
 * Immutable login snapshot loaded for password verification.
 * {@code password} and {@code sessionVersion} come from the same persistent row.
 * This type is not stored in the HTTP session; {@link #eraseCredentials()} clears the hash
 * after {@code AuthenticationManager} succeeds.
 */
public final class AuthenticationUserDetails implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final Role role;
    private final long sessionVersion;
    private final AccountStatus accountStatus;
    private final Boolean emailVerified;
    private final List<GrantedAuthority> authorities;
    private transient String password;

    public AuthenticationUserDetails(
            Long userId,
            String email,
            String password,
            Role role,
            long sessionVersion,
            AccountStatus accountStatus,
            Boolean emailVerified
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be null or blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (accountStatus == null) {
            throw new IllegalArgumentException("accountStatus must not be null");
        }
        this.userId = userId;
        this.email = email.trim();
        this.password = password;
        this.role = role;
        this.sessionVersion = sessionVersion;
        this.accountStatus = accountStatus;
        this.emailVerified = emailVerified;
        this.authorities = List.of(new SimpleGrantedAuthority(role.name()));
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public long getSessionVersion() {
        return sessionVersion;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    @Override
    public String toString() {
        return "AuthenticationUserDetails[userId=" + userId + ", sessionVersion=" + sessionVersion + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthenticationUserDetails that)) {
            return false;
        }
        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
