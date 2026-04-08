package it.zuperman.support_trainer.profile.dto.response;

import java.time.LocalDateTime;

import it.zuperman.support_trainer.common.entity.User;

public class MyAccountResponse {

    private Long id;
    private String email;
    private String role;
    private String accountStatus;
    private Boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MyAccountResponse() {
    }

    public MyAccountResponse(
            Long id,
            String email,
            String role,
            String accountStatus,
            Boolean emailVerified,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.email = email;
        this.role = role;
        this.accountStatus = accountStatus;
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static MyAccountResponse fromUser(User user) {
        return new MyAccountResponse(
                user.getId(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getAccountStatus() != null ? user.getAccountStatus().name() : null,
                user.getEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}