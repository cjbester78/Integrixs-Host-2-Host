package com.integrixs.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * Immutable token response DTO for authentication
 * Contains all token-related information returned to client
 */
public record TokenResponse(
    @JsonProperty("access_token")
    String accessToken,
    
    @JsonProperty("refresh_token") 
    String refreshToken,
    
    @JsonProperty("token_type")
    String tokenType,
    
    @JsonProperty("expires_in")
    long expiresIn, // seconds
    
    @JsonProperty("username")
    String username,
    
    @JsonProperty("full_name")
    String fullName,
    
    @JsonProperty("role")
    String role,
    
    @JsonProperty("issued_at")
    LocalDateTime issuedAt,
    
    @JsonProperty("expires_at")
    LocalDateTime expiresAt
) {
    
    /**
     * Builder for creating TokenResponse instances
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for TokenResponse
     */
    public static class Builder {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private long expiresIn;
        private String username;
        private String fullName;
        private String role;
        private LocalDateTime issuedAt = LocalDateTime.now();
        private LocalDateTime expiresAt;
        
        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }
        
        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }
        
        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }
        
        public Builder expiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }
        
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        
        public Builder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }
        
        public Builder role(String role) {
            this.role = role;
            return this;
        }
        
        public Builder issuedAt(LocalDateTime issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }
        
        public Builder expiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }
        
        public TokenResponse build() {
            if (accessToken == null || accessToken.trim().isEmpty()) {
                throw new IllegalArgumentException("Access token cannot be null or empty");
            }
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be null or empty");
            }
            if (role == null || role.trim().isEmpty()) {
                throw new IllegalArgumentException("Role cannot be null or empty");
            }
            if (expiresAt == null && expiresIn > 0) {
                expiresAt = issuedAt.plusSeconds(expiresIn);
            }
            
            return new TokenResponse(
                accessToken, refreshToken, tokenType, expiresIn,
                username, fullName, role, issuedAt, expiresAt
            );
        }
    }
}