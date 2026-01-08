package com.integrixs.backend.service;

import com.integrixs.backend.dto.response.TokenResponse;
import com.integrixs.backend.model.User;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JWT Token Management Service Interface
 * Provides abstraction for JWT token operations following OOP principles
 */
public interface JwtTokenManagementService {
    
    /**
     * Generate complete token response for authenticated user
     */
    TokenResponse generateTokenResponse(User user);
    
    /**
     * Generate complete token response from authentication
     */
    TokenResponse generateTokenResponse(Authentication authentication);
    
    /**
     * Validate JWT token and return validation result
     */
    TokenValidationResult validateToken(String token);
    
    /**
     * Extract user information from token
     */
    Optional<TokenUserInfo> extractUserInfo(String token);
    
    /**
     * Refresh access token using refresh token
     */
    Optional<TokenResponse> refreshAccessToken(String refreshToken);
    
    /**
     * Get token expiration information
     */
    TokenExpirationInfo getTokenExpirationInfo(String token);
    
    /**
     * Check if token is about to expire
     */
    boolean isTokenNearExpiry(String token, int minutesThreshold);
    
    /**
     * Check if token is of specific type
     */
    boolean isTokenOfType(String token, TokenType tokenType);
    
    /**
     * Get remaining time before token expires
     */
    long getTimeUntilExpiration(String token);
    
    /**
     * Token types enumeration
     */
    enum TokenType {
        ACCESS, REFRESH
    }
    
    /**
     * Immutable token validation result
     */
    record TokenValidationResult(
        boolean valid,
        String reason,
        LocalDateTime validatedAt,
        Optional<String> userId,
        Optional<String> username,
        Optional<String> role
    ) {
        public static TokenValidationResult valid(String userId, String username, String role) {
            return new TokenValidationResult(
                true, null, LocalDateTime.now(), 
                Optional.of(userId), Optional.of(username), Optional.of(role)
            );
        }
        
        public static TokenValidationResult invalid(String reason) {
            return new TokenValidationResult(
                false, reason, LocalDateTime.now(), 
                Optional.empty(), Optional.empty(), Optional.empty()
            );
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getFailureReason() {
            return reason != null ? reason : "Unknown validation failure";
        }
    }
    
    /**
     * Immutable token user information
     */
    record TokenUserInfo(
        String userId,
        String username,
        String email,
        String fullName,
        String role,
        boolean enabled,
        TokenType tokenType,
        LocalDateTime extractedAt
    ) {
        
        public static TokenUserInfo from(String userId, String username, String email,
                                       String fullName, String role, boolean enabled,
                                       TokenType tokenType) {
            return new TokenUserInfo(userId, username, email, fullName, role, 
                                   enabled, tokenType, LocalDateTime.now());
        }
        
        public boolean isAccessToken() {
            return tokenType == TokenType.ACCESS;
        }
        
        public boolean isRefreshToken() {
            return tokenType == TokenType.REFRESH;
        }
    }
    
    /**
     * Immutable token expiration information
     */
    record TokenExpirationInfo(
        LocalDateTime expiresAt,
        long expiresInMillis,
        boolean expired,
        boolean nearExpiry,
        int minutesUntilExpiry
    ) {
        
        public static TokenExpirationInfo create(LocalDateTime expiresAt, int nearExpiryThreshold) {
            LocalDateTime now = LocalDateTime.now();
            long expiresInMillis = java.time.Duration.between(now, expiresAt).toMillis();
            boolean expired = expiresAt.isBefore(now);
            int minutesUntilExpiry = (int) java.time.Duration.between(now, expiresAt).toMinutes();
            boolean nearExpiry = !expired && minutesUntilExpiry <= nearExpiryThreshold;
            
            return new TokenExpirationInfo(expiresAt, expiresInMillis, expired, 
                                         nearExpiry, minutesUntilExpiry);
        }
        
        public boolean isExpiredOrNearExpiry() {
            return expired || nearExpiry;
        }
        
        public long getRemainingMillis() {
            return Math.max(0, expiresInMillis);
        }
    }
}