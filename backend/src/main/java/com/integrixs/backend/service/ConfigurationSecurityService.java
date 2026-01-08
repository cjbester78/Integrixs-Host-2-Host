package com.integrixs.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration Security Service Interface
 * Provides security validation and protection for configuration operations
 */
public interface ConfigurationSecurityService {
    
    /**
     * Validate if user is authorized to access configuration key
     */
    ConfigurationAuthorizationResult authorizeAccess(String userId, String configKey, ConfigurationOperation operation);
    
    /**
     * Validate configuration change for security risks
     */
    ConfigurationSecurityValidationResult validateConfigurationSecurity(String configKey, String newValue, String userId);
    
    /**
     * Encrypt sensitive configuration values
     */
    String encryptSensitiveValue(String key, String value);
    
    /**
     * Decrypt sensitive configuration values
     */
    Optional<String> decryptSensitiveValue(String key, String encryptedValue);
    
    /**
     * Check if configuration key contains sensitive information
     */
    boolean isSensitiveConfiguration(String key);
    
    /**
     * Get masked value for logging purposes
     */
    String getMaskedValue(String key, String value);
    
    /**
     * Audit configuration access
     */
    void auditConfigurationAccess(String userId, String configKey, ConfigurationOperation operation, boolean authorized);
    
    /**
     * Get configuration security policies
     */
    List<ConfigurationSecurityPolicy> getSecurityPolicies();
    
    /**
     * Check for suspicious configuration patterns
     */
    SuspiciousConfigurationReport checkSuspiciousActivity(String userId, List<ConfigurationAccessAttempt> recentAttempts);
    
    /**
     * Configuration operation enumeration
     */
    enum ConfigurationOperation {
        READ, CREATE, UPDATE, DELETE, EXPORT, IMPORT
    }
    
    /**
     * Configuration authorization result
     */
    record ConfigurationAuthorizationResult(
        boolean authorized,
        String reason,
        List<String> requiredPermissions,
        SecurityLevel requiredSecurityLevel,
        LocalDateTime checkedAt
    ) {
        
        public static ConfigurationAuthorizationResult createAuthorized() {
            return new ConfigurationAuthorizationResult(true, null, List.of(), 
                SecurityLevel.STANDARD, LocalDateTime.now());
        }
        
        public static ConfigurationAuthorizationResult denied(String reason, List<String> requiredPermissions) {
            return new ConfigurationAuthorizationResult(false, reason, requiredPermissions, 
                SecurityLevel.ADMINISTRATOR, LocalDateTime.now());
        }
        
        public static ConfigurationAuthorizationResult deniedSecurityLevel(SecurityLevel required) {
            return new ConfigurationAuthorizationResult(false, 
                "Insufficient security level", List.of(), required, LocalDateTime.now());
        }
    }
    
    /**
     * Configuration security validation result
     */
    record ConfigurationSecurityValidationResult(
        boolean secure,
        List<String> securityConcerns,
        SecurityRiskLevel riskLevel,
        List<String> recommendations,
        boolean requiresAdditionalApproval,
        LocalDateTime validatedAt
    ) {
        
        public static ConfigurationSecurityValidationResult createSecure() {
            return new ConfigurationSecurityValidationResult(true, List.of(), 
                SecurityRiskLevel.LOW, List.of(), false, LocalDateTime.now());
        }
        
        public static ConfigurationSecurityValidationResult insecure(List<String> concerns, 
                                                                    SecurityRiskLevel risk,
                                                                    List<String> recommendations) {
            return new ConfigurationSecurityValidationResult(false, concerns, risk, 
                recommendations, risk.ordinal() >= SecurityRiskLevel.HIGH.ordinal(), LocalDateTime.now());
        }
        
        public boolean isHighRisk() {
            return riskLevel == SecurityRiskLevel.CRITICAL || riskLevel == SecurityRiskLevel.HIGH;
        }
    }
    
    /**
     * Configuration security policy
     */
    record ConfigurationSecurityPolicy(
        String keyPattern,
        List<String> allowedRoles,
        SecurityLevel requiredSecurityLevel,
        boolean requiresEncryption,
        boolean auditRequired,
        List<String> validationRules
    ) {}
    
    /**
     * Configuration access attempt
     */
    record ConfigurationAccessAttempt(
        String userId,
        String configKey,
        ConfigurationOperation operation,
        boolean successful,
        String failureReason,
        LocalDateTime timestamp,
        Map<String, String> context
    ) {}
    
    /**
     * Suspicious configuration report
     */
    record SuspiciousConfigurationReport(
        String userId,
        boolean suspicious,
        int suspicionScore,
        List<SuspiciousPattern> patterns,
        int totalAttempts,
        LocalDateTime analysisTime,
        String recommendation
    ) {
        
        public boolean requiresInvestigation() {
            return suspicionScore >= 70;
        }
        
        public boolean isHighRisk() {
            return suspicionScore >= 85;
        }
    }
    
    /**
     * Suspicious pattern detection
     */
    record SuspiciousPattern(
        SuspiciousPatternType type,
        String description,
        int occurrences,
        int severityScore
    ) {}
    
    // Enumerations
    
    enum SecurityLevel {
        STANDARD, ELEVATED, ADMINISTRATOR, SYSTEM
    }
    
    enum SecurityRiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    enum SuspiciousPatternType {
        RAPID_CONFIGURATION_CHANGES,
        SENSITIVE_DATA_ACCESS_PATTERN,
        UNUSUAL_TIME_ACCESS,
        BULK_CONFIGURATION_EXPORT,
        SECURITY_BYPASS_ATTEMPT,
        UNAUTHORIZED_SYSTEM_CONFIG_ACCESS
    }
}