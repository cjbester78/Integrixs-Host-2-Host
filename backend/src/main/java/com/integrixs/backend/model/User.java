package com.integrixs.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * User model for authentication and authorization without JPA
 * Supports two roles: ADMINISTRATOR and VIEWER
 */
public class User implements UserDetails {

    private UUID id;
    private String username;
    private String email;
    @JsonIgnore
    private String password;
    private String fullName;
    private UserRole role;
    private boolean enabled = true;
    private boolean accountNonExpired = true;
    private boolean accountNonLocked = true;
    private boolean credentialsNonExpired = true;
    private String timezone = "UTC";
    private int failedLoginAttempts = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime lastLogin;

    // User roles enum
    public enum UserRole {
        ADMINISTRATOR("Administrator - Full access to all features"),
        VIEWER("Viewer - Read-only access to monitoring and logs"),
        INTEGRATOR("Integrator - System role for automated flow execution");

        private final String description;

        UserRole(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // Constructors
    public User() {
        this.id = UUID.randomUUID();
    }

    public User(String username, String email, String password, String fullName, UserRole role) {
        this();
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    // UserDetails implementation
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // Helper methods
    public boolean isAdministrator() {
        return role == UserRole.ADMINISTRATOR;
    }

    public boolean isViewer() {
        return role == UserRole.VIEWER;
    }

    public boolean isIntegrator() {
        return role == UserRole.INTEGRATOR;
    }

    public boolean hasFullAccess() {
        return isAdministrator();
    }

    public boolean canExecuteFlows() {
        return isIntegrator() || isAdministrator();
    }

    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void lockAccount() {
        this.accountNonLocked = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void unlockAccount() {
        this.accountNonLocked = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDisplayName() {
        return fullName != null && !fullName.trim().isEmpty() ? fullName : username;
    }

    public String getRoleDisplayName() {
        return role != null ? role.getDescription() : "Unknown Role";
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { 
        this.username = username;
        this.updatedAt = LocalDateTime.now();
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { 
        this.email = email;
        this.updatedAt = LocalDateTime.now();
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { 
        this.password = password;
        this.updatedAt = LocalDateTime.now();
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { 
        this.fullName = fullName;
        this.updatedAt = LocalDateTime.now();
    }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { 
        this.role = role;
        this.updatedAt = LocalDateTime.now();
    }

    public void setEnabled(boolean enabled) { 
        this.enabled = enabled;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAccountNonExpired(boolean accountNonExpired) { 
        this.accountNonExpired = accountNonExpired;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAccountNonLocked(boolean accountNonLocked) { 
        this.accountNonLocked = accountNonLocked;
        this.updatedAt = LocalDateTime.now();
    }

    public void setCredentialsNonExpired(boolean credentialsNonExpired) { 
        this.credentialsNonExpired = credentialsNonExpired;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { 
        this.timezone = timezone;
        this.updatedAt = LocalDateTime.now();
    }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { 
        this.failedLoginAttempts = failedLoginAttempts;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLogin() { return lastLogin; }
    public void setLastLogin(LocalDateTime lastLogin) { this.lastLogin = lastLogin; }

    @Override
    public String toString() {
        return String.format("User{id=%s, username='%s', email='%s', role=%s, enabled=%s}", 
                id, username, email, role, enabled);
    }
}