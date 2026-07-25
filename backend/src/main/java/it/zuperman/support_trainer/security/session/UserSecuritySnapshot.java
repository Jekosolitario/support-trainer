package it.zuperman.support_trainer.security.session;

import java.util.Objects;

import it.zuperman.support_trainer.common.enums.AccountStatus;
import it.zuperman.support_trainer.common.enums.Role;

/**
 * Minimal readiness projection for future session authentication checks.
 * Contains only the fields required to validate account readiness and role coherence.
 */
public final class UserSecuritySnapshot {

    private final Long id;
    private final Role role;
    private final AccountStatus accountStatus;
    private final Boolean emailVerified;

    public UserSecuritySnapshot(
            Long id,
            Role role,
            AccountStatus accountStatus,
            Boolean emailVerified
    ) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (accountStatus == null) {
            throw new IllegalArgumentException("accountStatus must not be null");
        }
        if (emailVerified == null) {
            throw new IllegalArgumentException("emailVerified must not be null");
        }
        this.id = id;
        this.role = role;
        this.accountStatus = accountStatus;
        this.emailVerified = emailVerified;
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserSecuritySnapshot that)) {
            return false;
        }
        return id.equals(that.id)
                && role == that.role
                && accountStatus == that.accountStatus
                && emailVerified.equals(that.emailVerified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, role, accountStatus, emailVerified);
    }
}
