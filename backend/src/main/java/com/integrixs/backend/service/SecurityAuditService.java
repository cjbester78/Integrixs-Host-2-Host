package com.integrixs.backend.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Security Audit Service Interface
 * Provides comprehensive security event logging and monitoring capabilities
 */
public interface SecurityAuditService {
    
    /**
     * Log authentication attempt
     */
    void logAuthenticationAttempt(AuthenticationAuditEvent event);
    
    /**
     * Log authorization decision
     */
    void logAuthorizationDecision(AuthorizationAuditEvent event);
    
    /**
     * Log security violation
     */
    void logSecurityViolation(SecurityViolationEvent event);
    
    /**
     * Log token operation
     */
    void logTokenOperation(TokenAuditEvent event);
    
    /**
     * Log configuration security event
     */
    void logConfigurationSecurityEvent(ConfigurationSecurityEvent event);
    
    /**
     * Get security events for a user
     */
    SecurityEventSummary getSecurityEventsForUser(String userId, LocalDateTime fromDate, LocalDateTime toDate);
    
    /**
     * Get recent security violations
     */
    SecurityEventSummary getRecentSecurityViolations(int hours);
    
    /**
     * Check for suspicious activity patterns
     */
    SuspiciousActivityReport checkSuspiciousActivity(String userId, int hoursToCheck);
    
    /**
     * Authentication audit event
     */
    record AuthenticationAuditEvent(
        String username,
        String ipAddress,
        String userAgent,
        AuthenticationResult result,
        String failureReason,
        LocalDateTime timestamp,
        Map<String, Object> additionalData
    ) {
        
        public static AuthenticationAuditEvent success(String username, String ipAddress, String userAgent) {
            return new AuthenticationAuditEvent(username, ipAddress, userAgent, 
                AuthenticationResult.SUCCESS, null, LocalDateTime.now(), Map.of());
        }
        
        public static AuthenticationAuditEvent failure(String username, String ipAddress, 
                                                      String userAgent, String reason) {
            return new AuthenticationAuditEvent(username, ipAddress, userAgent, 
                AuthenticationResult.FAILURE, reason, LocalDateTime.now(), Map.of());
        }
        
        public static AuthenticationAuditEvent locked(String username, String ipAddress, String userAgent) {
            return new AuthenticationAuditEvent(username, ipAddress, userAgent, 
                AuthenticationResult.ACCOUNT_LOCKED, "Account locked due to multiple failed attempts", 
                LocalDateTime.now(), Map.of());
        }
    }
    
    /**
     * Authorization audit event
     */
    record AuthorizationAuditEvent(
        String userId,
        String username,
        String resource,
        String action,
        AuthorizationResult result,
        String role,
        String denialReason,
        LocalDateTime timestamp,
        Map<String, Object> context
    ) {
        
        public static AuthorizationAuditEvent granted(String userId, String username, 
                                                    String resource, String action, String role) {
            return new AuthorizationAuditEvent(userId, username, resource, action, 
                AuthorizationResult.GRANTED, role, null, LocalDateTime.now(), Map.of());
        }
        
        public static AuthorizationAuditEvent denied(String userId, String username, 
                                                   String resource, String action, 
                                                   String role, String reason) {
            return new AuthorizationAuditEvent(userId, username, resource, action, 
                AuthorizationResult.DENIED, role, reason, LocalDateTime.now(), Map.of());
        }
    }
    
    /**
     * Security violation event
     */
    record SecurityViolationEvent(
        String userId,
        String ipAddress,
        ViolationType violationType,
        String description,
        SeverityLevel severity,
        String resource,
        LocalDateTime timestamp,
        Map<String, Object> evidence
    ) {
        
        public static SecurityViolationEvent create(String userId, String ipAddress,
                                                   ViolationType type, String description,
                                                   SeverityLevel severity, String resource) {
            return new SecurityViolationEvent(userId, ipAddress, type, description, 
                severity, resource, LocalDateTime.now(), Map.of());
        }
    }
    
    /**
     * Token audit event
     */
    record TokenAuditEvent(
        String userId,
        String username,
        TokenOperation operation,
        TokenType tokenType,
        boolean successful,
        String failureReason,
        String ipAddress,
        LocalDateTime timestamp,
        Map<String, Object> metadata
    ) {
        
        public static TokenAuditEvent success(String userId, String username, 
                                            TokenOperation operation, TokenType tokenType, 
                                            String ipAddress) {
            return new TokenAuditEvent(userId, username, operation, tokenType, 
                true, null, ipAddress, LocalDateTime.now(), Map.of());
        }
        
        public static TokenAuditEvent failure(String userId, String username,
                                            TokenOperation operation, TokenType tokenType,
                                            String ipAddress, String reason) {
            return new TokenAuditEvent(userId, username, operation, tokenType, 
                false, reason, ipAddress, LocalDateTime.now(), Map.of());
        }
    }
    
    /**
     * Configuration security event
     */
    record ConfigurationSecurityEvent(
        String userId,
        String configKey,
        ConfigurationOperation operation,
        String oldValue,
        String newValue,
        boolean authorized,
        String denialReason,
        LocalDateTime timestamp
    ) {
        
        public static ConfigurationSecurityEvent authorized(String userId, String configKey,
                                                          ConfigurationOperation operation,
                                                          String oldValue, String newValue) {
            return new ConfigurationSecurityEvent(userId, configKey, operation, 
                oldValue, newValue, true, null, LocalDateTime.now());
        }
        
        public static ConfigurationSecurityEvent unauthorized(String userId, String configKey,
                                                             ConfigurationOperation operation,
                                                             String reason) {
            return new ConfigurationSecurityEvent(userId, configKey, operation, 
                null, null, false, reason, LocalDateTime.now());
        }
    }
    
    /**
     * Security event summary
     */
    record SecurityEventSummary(
        int totalEvents,
        int authenticationFailures,
        int authorizationDenials,
        int securityViolations,
        int tokenFailures,
        LocalDateTime reportGeneratedAt,
        Optional<LocalDateTime> lastSuspiciousActivity
    ) {}
    
    /**
     * Suspicious activity report
     */
    record SuspiciousActivityReport(
        String userId,
        boolean suspicious,
        int suspicionScore, // 0-100
        SuspiciousActivityType primaryConcern,
        String description,
        int eventsAnalyzed,
        LocalDateTime analysisTime,
        Map<SuspiciousActivityType, Integer> activityBreakdown
    ) {
        
        public boolean isHighRisk() {
            return suspicionScore >= 70;
        }
        
        public boolean isMediumRisk() {
            return suspicionScore >= 40 && suspicionScore < 70;
        }
        
        public boolean requiresInvestigation() {
            return suspicionScore >= 50;
        }
    }
    
    // Enumerations
    
    enum AuthenticationResult {
        SUCCESS, FAILURE, ACCOUNT_LOCKED, ACCOUNT_DISABLED
    }
    
    enum AuthorizationResult {
        GRANTED, DENIED
    }
    
    enum ViolationType {
        UNAUTHORIZED_ACCESS_ATTEMPT,
        PRIVILEGE_ESCALATION,
        SUSPICIOUS_TOKEN_USAGE,
        CONFIGURATION_TAMPERING,
        UNUSUAL_API_USAGE,
        BRUTE_FORCE_ATTACK,
        SESSION_HIJACKING_ATTEMPT
    }
    
    enum SeverityLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    enum TokenOperation {
        GENERATE, VALIDATE, REFRESH, REVOKE, EXPIRE
    }
    
    enum TokenType {
        ACCESS, REFRESH
    }
    
    enum ConfigurationOperation {
        READ, CREATE, UPDATE, DELETE
    }
    
    enum SuspiciousActivityType {
        RAPID_FAILED_LOGINS,
        UNUSUAL_ACCESS_PATTERNS,
        PRIVILEGE_ESCALATION_ATTEMPTS,
        UNUSUAL_IP_ADDRESSES,
        OFF_HOURS_ACTIVITY,
        BULK_DATA_ACCESS,
        CONFIGURATION_CHANGES
    }
}