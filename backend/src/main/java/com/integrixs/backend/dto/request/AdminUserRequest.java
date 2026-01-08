package com.integrixs.backend.dto.request;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable request DTO for administrative user operations.
 * Contains user data and operation parameters for user management.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class AdminUserRequest {
    
    private final String operation;
    private final UUID userId;
    private final String username;
    private final String email;
    private final String role;
    private final String password;
    private final Boolean enabled;
    private final String reason;
    private final UUID requestedBy;
    private final LocalDateTime requestedAt;
    
    private AdminUserRequest(Builder builder) {
        this.operation = builder.operation;
        this.userId = builder.userId;
        this.username = builder.username;
        this.email = builder.email;
        this.role = builder.role;
        this.password = builder.password;
        this.enabled = builder.enabled;
        this.reason = builder.reason;
        this.requestedBy = builder.requestedBy;
        this.requestedAt = builder.requestedAt != null ? builder.requestedAt : LocalDateTime.now();
    }
    
    // Getters
    public String getOperation() { return operation; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getPassword() { return password; }
    public Boolean getEnabled() { return enabled; }
    public String getReason() { return reason; }
    public UUID getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    
    /**
     * Check if operation is a user creation.
     */
    public boolean isCreateOperation() {
        return "create".equalsIgnoreCase(operation);
    }
    
    /**
     * Check if operation is a user update.
     */
    public boolean isUpdateOperation() {
        return "update".equalsIgnoreCase(operation);
    }
    
    /**
     * Check if operation is a user deletion.
     */
    public boolean isDeleteOperation() {
        return "delete".equalsIgnoreCase(operation);
    }
    
    /**
     * Check if operation involves password change.
     */
    public boolean hasPasswordChange() {
        return password != null && !password.trim().isEmpty();
    }
    
    /**
     * Check if request has user data.
     */
    public boolean hasUserData() {
        return username != null || email != null || role != null;
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create request for user creation.
     */
    public static AdminUserRequest createRequest(String username, String email, String role, String password, UUID requestedBy) {
        return builder()
            .operation("create")
            .username(username)
            .email(email)
            .role(role)
            .password(password)
            .enabled(true)
            .requestedBy(requestedBy)
            .reason("Create new user")
            .build();
    }
    
    /**
     * Create request for user update.
     */
    public static AdminUserRequest updateRequest(UUID userId, String email, String role, Boolean enabled, UUID requestedBy) {
        return builder()
            .operation("update")
            .userId(userId)
            .email(email)
            .role(role)
            .enabled(enabled)
            .requestedBy(requestedBy)
            .reason("Update user details")
            .build();
    }
    
    /**
     * Create request for password reset.
     */
    public static AdminUserRequest passwordResetRequest(UUID userId, String newPassword, UUID requestedBy) {
        return builder()
            .operation("password_reset")
            .userId(userId)
            .password(newPassword)
            .requestedBy(requestedBy)
            .reason("Password reset")
            .build();
    }
    
    /**
     * Create request for user deletion.
     */
    public static AdminUserRequest deleteRequest(UUID userId, String reason, UUID requestedBy) {
        return builder()
            .operation("delete")
            .userId(userId)
            .reason(reason)
            .requestedBy(requestedBy)
            .build();
    }
    
    /**
     * Create request for user status change.
     */
    public static AdminUserRequest statusChangeRequest(UUID userId, boolean enabled, UUID requestedBy) {
        return builder()
            .operation("status_change")
            .userId(userId)
            .enabled(enabled)
            .requestedBy(requestedBy)
            .reason(enabled ? "Enable user" : "Disable user")
            .build();
    }
    
    /**
     * Builder for AdminUserRequest.
     */
    public static class Builder {
        private String operation;
        private UUID userId;
        private String username;
        private String email;
        private String role;
        private String password;
        private Boolean enabled;
        private String reason;
        private UUID requestedBy;
        private LocalDateTime requestedAt;
        
        private Builder() {}
        
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }
        
        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username != null ? username.trim().toLowerCase() : null;
            return this;
        }
        
        public Builder email(String email) {
            this.email = email != null ? email.trim().toLowerCase() : null;
            return this;
        }
        
        public Builder role(String role) {
            this.role = role != null ? role.toUpperCase() : null;
            return this;
        }
        
        public Builder password(String password) {
            this.password = password;
            return this;
        }
        
        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }
        
        public Builder requestedBy(UUID requestedBy) {
            this.requestedBy = requestedBy;
            return this;
        }
        
        public Builder requestedAt(LocalDateTime requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }
        
        public AdminUserRequest build() {
            return new AdminUserRequest(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AdminUserRequest{operation='%s', userId=%s, username='%s', " +
                           "email='%s', role='%s', enabled=%s, requestedBy=%s, requestedAt=%s}", 
                           operation, userId, username, email, role, enabled, requestedBy, requestedAt);
    }
}