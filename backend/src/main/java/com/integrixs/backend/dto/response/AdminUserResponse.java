package com.integrixs.backend.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable response DTO for administrative user operations.
 * Contains user data and operation results for user management.
 * Part of Phase 4 controller layer refactoring following OOP principles.
 */
public final class AdminUserResponse {
    
    private final String operation;
    private final String status;
    private final UUID userId;
    private final String username;
    private final String email;
    private final String role;
    private final Boolean enabled;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastLogin;
    private final String message;
    private final LocalDateTime timestamp;
    
    private AdminUserResponse(Builder builder) {
        this.operation = builder.operation;
        this.status = builder.status;
        this.userId = builder.userId;
        this.username = builder.username;
        this.email = builder.email;
        this.role = builder.role;
        this.enabled = builder.enabled;
        this.createdAt = builder.createdAt;
        this.lastLogin = builder.lastLogin;
        this.message = builder.message;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
    }
    
    // Getters
    public String getOperation() { return operation; }
    public String getStatus() { return status; }
    public UUID getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public Boolean getEnabled() { return enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLogin() { return lastLogin; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
    
    /**
     * Check if operation was successful.
     */
    public boolean isOperationSuccessful() {
        return "SUCCESS".equalsIgnoreCase(status) || "CREATED".equalsIgnoreCase(status);
    }
    
    /**
     * Check if user is active.
     */
    public boolean isUserActive() {
        return Boolean.TRUE.equals(enabled);
    }
    
    /**
     * Check if user is an administrator.
     */
    public boolean isAdministrator() {
        return "ADMINISTRATOR".equalsIgnoreCase(role);
    }
    
    /**
     * Check if user has logged in recently (within last 30 days).
     */
    public boolean hasRecentLogin() {
        return lastLogin != null && lastLogin.isAfter(LocalDateTime.now().minusDays(30));
    }
    
    /**
     * Get user display name (username or email).
     */
    public String getDisplayName() {
        return username != null && !username.trim().isEmpty() ? username : email;
    }
    
    /**
     * Get masked email for display purposes.
     */
    public String getMaskedEmail() {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];
        
        if (localPart.length() <= 2) {
            return email; // Don't mask very short emails
        }
        
        String maskedLocal = localPart.charAt(0) + "*".repeat(localPart.length() - 2) + localPart.charAt(localPart.length() - 1);
        return maskedLocal + "@" + domain;
    }
    
    /**
     * Create builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create successful user creation response.
     */
    public static AdminUserResponse createdResponse(UUID userId, String username, String email, String role) {
        return builder()
            .operation("create")
            .status("CREATED")
            .userId(userId)
            .username(username)
            .email(email)
            .role(role)
            .enabled(true)
            .message("User created successfully")
            .build();
    }
    
    /**
     * Create successful user update response.
     */
    public static AdminUserResponse updatedResponse(UUID userId, String message) {
        return builder()
            .operation("update")
            .status("SUCCESS")
            .userId(userId)
            .message(message)
            .build();
    }
    
    /**
     * Create successful user deletion response.
     */
    public static AdminUserResponse deletedResponse(UUID userId, String username) {
        return builder()
            .operation("delete")
            .status("SUCCESS")
            .userId(userId)
            .username(username)
            .message("User deleted successfully")
            .build();
    }
    
    /**
     * Create user details response.
     */
    public static AdminUserResponse detailsResponse(UUID userId, String username, String email, String role, 
                                                  Boolean enabled, LocalDateTime createdAt, LocalDateTime lastLogin) {
        return builder()
            .operation("details")
            .status("SUCCESS")
            .userId(userId)
            .username(username)
            .email(email)
            .role(role)
            .enabled(enabled)
            .createdAt(createdAt)
            .lastLogin(lastLogin)
            .build();
    }
    
    /**
     * Create password reset response.
     */
    public static AdminUserResponse passwordResetResponse(UUID userId, String username) {
        return builder()
            .operation("password_reset")
            .status("SUCCESS")
            .userId(userId)
            .username(username)
            .message("Password reset successfully")
            .build();
    }
    
    /**
     * Create user status change response.
     */
    public static AdminUserResponse statusChangeResponse(UUID userId, String username, boolean enabled) {
        String message = enabled ? "User enabled successfully" : "User disabled successfully";
        return builder()
            .operation("status_change")
            .status("SUCCESS")
            .userId(userId)
            .username(username)
            .enabled(enabled)
            .message(message)
            .build();
    }
    
    /**
     * Builder for AdminUserResponse.
     */
    public static class Builder {
        private String operation;
        private String status;
        private UUID userId;
        private String username;
        private String email;
        private String role;
        private Boolean enabled;
        private LocalDateTime createdAt;
        private LocalDateTime lastLogin;
        private String message;
        private LocalDateTime timestamp;
        
        private Builder() {}
        
        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public Builder userId(UUID userId) {
            this.userId = userId;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder email(String email) {
            this.email = email;
            return this;
        }
        
        public Builder role(String role) {
            this.role = role != null ? role.toUpperCase() : null;
            return this;
        }
        
        public Builder enabled(Boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder lastLogin(LocalDateTime lastLogin) {
            this.lastLogin = lastLogin;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public AdminUserResponse build() {
            return new AdminUserResponse(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("AdminUserResponse{operation='%s', status='%s', " +
                           "userId=%s, username='%s', role='%s', enabled=%s, timestamp=%s}", 
                           operation, status, userId, username, role, enabled, timestamp);
    }
}