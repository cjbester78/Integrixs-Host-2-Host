package com.integrixs.core.service;

import com.integrixs.shared.model.SystemConfiguration;
import com.integrixs.core.repository.SystemConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for configuration security and access control.
 * Implements proper security patterns with validation and audit trails.
 * Follows OOP principles with immutable security result objects.
 */
@Service
public class ConfigurationSecurityService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationSecurityService.class);
    
    private final SystemConfigurationRepository configRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    
    public ConfigurationSecurityService(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
    }
    
    /**
     * Immutable security validation result
     */
    public static class SecurityValidationResult {
        private final boolean authorized;
        private final List<String> violations;
        private final String auditMessage;
        private final SecurityRisk riskLevel;
        
        private SecurityValidationResult(boolean authorized, List<String> violations, 
                                       String auditMessage, SecurityRisk riskLevel) {
            this.authorized = authorized;
            this.violations = new ArrayList<>(violations != null ? violations : new ArrayList<>());
            this.auditMessage = auditMessage;
            this.riskLevel = riskLevel;
        }
        
        public boolean isAuthorized() { return authorized; }
        public List<String> getViolations() { return new ArrayList<>(violations); }
        public String getAuditMessage() { return auditMessage; }
        public SecurityRisk getRiskLevel() { return riskLevel; }
        
        public static SecurityValidationResult authorized(String auditMessage) {
            return new SecurityValidationResult(true, null, auditMessage, SecurityRisk.LOW);
        }
        
        public static SecurityValidationResult authorizedWithRisk(String auditMessage, SecurityRisk riskLevel) {
            return new SecurityValidationResult(true, null, auditMessage, riskLevel);
        }
        
        public static SecurityValidationResult denied(List<String> violations, String auditMessage) {
            return new SecurityValidationResult(false, violations, auditMessage, SecurityRisk.HIGH);
        }
        
        public static SecurityValidationResult denied(String violation, String auditMessage) {
            List<String> violations = new ArrayList<>();
            violations.add(violation);
            return new SecurityValidationResult(false, violations, auditMessage, SecurityRisk.HIGH);
        }
    }
    
    /**
     * Security risk levels
     */
    public enum SecurityRisk {
        LOW("Low risk configuration change"),
        MEDIUM("Medium risk configuration change"),
        HIGH("High risk configuration change"),
        CRITICAL("Critical security configuration change");
        
        private final String description;
        
        SecurityRisk(String description) {
            this.description = description;
        }
        
        public String getDescription() { return description; }
    }
    
    /**
     * Validate user authorization for configuration change
     */
    public SecurityValidationResult validateConfigurationAccess(String configKey, String newValue, 
                                                               SystemConfiguration config, UUID userId) {
        List<String> violations = new ArrayList<>();
        
        // Check if user is authenticated
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return SecurityValidationResult.denied("User not authenticated", 
                "Unauthenticated access attempt for configuration: " + configKey);
        }
        
        // Check for sensitive configurations
        if (isSensitiveConfiguration(configKey) || isSensitiveValue(newValue)) {
            boolean requireAdminForSensitive = getRequireAdminForSensitive();
            if (requireAdminForSensitive && !hasAdminRole(auth)) {
                violations.add("Administrative privileges required for sensitive configurations");
            }
        }
        
        // Check readonly configurations
        if (config != null && config.isReadonly()) {
            violations.add("Configuration is marked as readonly");
        }
        
        // Validate value for security risks
        SecurityRisk riskLevel = assessSecurityRisk(configKey, newValue, config);
        if (riskLevel == SecurityRisk.CRITICAL && !hasAdminRole(auth)) {
            violations.add("Administrative privileges required for critical security configurations");
        }
        
        // Check for suspicious patterns
        List<String> suspiciousPatterns = detectSuspiciousPatterns(newValue);
        violations.addAll(suspiciousPatterns);
        
        if (violations.isEmpty()) {
            String auditMessage = String.format("User %s authorized to modify configuration '%s' (risk: %s)", 
                                               userId, configKey, riskLevel.name());
            return SecurityValidationResult.authorizedWithRisk(auditMessage, riskLevel);
        } else {
            String auditMessage = String.format("User %s denied access to configuration '%s': %s", 
                                               userId, configKey, String.join(", ", violations));
            return SecurityValidationResult.denied(violations, auditMessage);
        }
    }
    
    /**
     * Validate batch configuration changes
     */
    public Map<String, SecurityValidationResult> validateBatchConfigurationAccess(
            Map<String, String> configChanges, Map<String, SystemConfiguration> existingConfigs, UUID userId) {
        
        Map<String, SecurityValidationResult> results = new java.util.HashMap<>();
        
        for (Map.Entry<String, String> entry : configChanges.entrySet()) {
            String configKey = entry.getKey();
            String newValue = entry.getValue();
            SystemConfiguration config = existingConfigs.get(configKey);
            
            SecurityValidationResult result = validateConfigurationAccess(configKey, newValue, config, userId);
            results.put(configKey, result);
        }
        
        return results;
    }
    
    /**
     * Sanitize configuration value for security
     */
    public String sanitizeConfigurationValue(String value, SystemConfiguration config) {
        if (value == null) return null;
        
        String sanitized = value.trim();
        
        // Remove potential script injection patterns
        sanitized = sanitized.replaceAll("(?i)<script[^>]*>.*?</script>", "");
        sanitized = sanitized.replaceAll("(?i)javascript:", "");
        sanitized = sanitized.replaceAll("(?i)on\\w+\\s*=", "");
        
        // Remove potential SQL injection patterns
        sanitized = sanitized.replaceAll("(?i)(union|select|insert|update|delete|drop)\\s+", "");
        
        // Remove potential command injection patterns
        sanitized = sanitized.replaceAll("[;&|`$()]", "");
        
        // For sensitive configurations, additional sanitization
        if (isSensitiveConfiguration(config.getConfigKey())) {
            sanitized = sanitizeSecretValue(sanitized);
        }
        
        return sanitized;
    }
    
    /**
     * Hash sensitive configuration values for storage
     */
    public String hashSensitiveValue(String value) {
        if (value == null || value.isEmpty()) return value;
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            
            digest.update(salt);
            byte[] hash = digest.digest(value.getBytes());
            
            // Combine salt and hash for storage
            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);
            
            return Base64.getEncoder().encodeToString(combined);
            
        } catch (Exception e) {
            logger.error("Error hashing sensitive value: {}", e.getMessage());
            return value; // Fallback to original value
        }
    }
    
    /**
     * Assess security risk level for configuration change
     */
    public SecurityRisk assessSecurityRisk(String configKey, String newValue, SystemConfiguration config) {
        if (configKey == null) return SecurityRisk.LOW;
        
        String lowerKey = configKey.toLowerCase();
        
        // Critical security configurations
        if (lowerKey.contains("security") || lowerKey.contains("auth") || lowerKey.contains("ssl") ||
            lowerKey.contains("tls") || lowerKey.contains("certificate") || lowerKey.contains("encryption")) {
            return SecurityRisk.CRITICAL;
        }
        
        // High risk configurations
        if (lowerKey.contains("password") || lowerKey.contains("secret") || lowerKey.contains("key") ||
            lowerKey.contains("token") || lowerKey.contains("credential")) {
            return SecurityRisk.HIGH;
        }
        
        // Medium risk configurations
        if (lowerKey.contains("url") || lowerKey.contains("host") || lowerKey.contains("port") ||
            lowerKey.contains("path") || lowerKey.contains("directory")) {
            return SecurityRisk.MEDIUM;
        }
        
        // Check value for risks
        if (newValue != null) {
            if (newValue.contains("://") || newValue.contains("${") || newValue.contains("../")) {
                return SecurityRisk.MEDIUM;
            }
        }
        
        return SecurityRisk.LOW;
    }
    
    /**
     * Check if configuration key is sensitive
     */
    private boolean isSensitiveConfiguration(String configKey) {
        if (configKey == null) return false;
        
        String lowerKey = configKey.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("secret") || 
               lowerKey.contains("key") || 
               lowerKey.contains("token") || 
               lowerKey.contains("credential") ||
               lowerKey.contains("private") ||
               lowerKey.contains("auth");
    }
    
    /**
     * Check if value appears to be sensitive
     */
    private boolean isSensitiveValue(String value) {
        if (value == null || value.length() < 8) return false;
        
        // Check for patterns that look like secrets
        return value.matches(".*[A-Za-z0-9+/]{16,}={0,2}") || // Base64-like
               value.matches(".*[a-fA-F0-9]{32,}") || // Hex-like
               value.startsWith("-----BEGIN") || // PEM format
               value.contains("://") && value.contains("@"); // URL with credentials
    }
    
    /**
     * Check if user has admin role
     */
    private boolean hasAdminRole(Authentication auth) {
        return auth.getAuthorities().stream()
                  .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMINISTRATOR") ||
                                       authority.getAuthority().equals("ADMINISTRATOR"));
    }
    
    /**
     * Detect suspicious patterns in configuration values
     */
    private List<String> detectSuspiciousPatterns(String value) {
        List<String> violations = new ArrayList<>();
        
        if (value == null) return violations;
        
        // Check for script injection
        if (Pattern.compile("(?i)<script[^>]*>").matcher(value).find()) {
            violations.add("Potential script injection detected");
        }
        
        // Check for SQL injection patterns
        if (Pattern.compile("(?i)(union|select|insert|update|delete|drop)\\s+").matcher(value).find()) {
            violations.add("Potential SQL injection pattern detected");
        }
        
        // Check for command injection
        if (Pattern.compile("[;&|`$()]").matcher(value).find()) {
            violations.add("Potential command injection characters detected");
        }
        
        // Check for path traversal
        if (value.contains("../") || value.contains("..\\")) {
            violations.add("Path traversal pattern detected");
        }
        
        // Check for template injection
        if (value.contains("${") || value.contains("#{")) {
            violations.add("Potential template injection detected");
        }
        
        return violations;
    }
    
    /**
     * Sanitize secret values
     */
    private String sanitizeSecretValue(String value) {
        // Remove common secret prefixes/suffixes
        String sanitized = value.replaceAll("(?i)^(secret|password|key)[:=]?\\s*", "");
        sanitized = sanitized.replaceAll("\\s*(secret|password|key)$", "");
        
        // Remove quotes
        sanitized = sanitized.replaceAll("^['\"]", "").replaceAll("['\"]$", "");
        
        return sanitized;
    }
    
    /**
     * Get security audit information
     */
    public Map<String, Object> getSecurityAuditInfo() {
        Map<String, Object> info = new java.util.HashMap<>();
        
        info.put("requireAdminForSensitive", getRequireAdminForSensitive());
        info.put("auditAllChanges", getAuditAllChanges());
        info.put("maxFailedAttempts", getMaxFailedAttempts());
        info.put("lockoutDurationMinutes", getLockoutDurationMinutes());
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            info.put("currentUser", auth.getName());
            info.put("hasAdminRole", hasAdminRole(auth));
        }
        
        return info;
    }
    
    /**
     * Helper methods to retrieve security settings from database
     */
    private boolean getRequireAdminForSensitive() {
        try {
            return configRepository.getBooleanValue("config.security.require-admin-for-sensitive", true);
        } catch (Exception e) {
            logger.warn("Error reading require-admin-for-sensitive configuration, using default: {}", e.getMessage());
            return true;
        }
    }
    
    private boolean getAuditAllChanges() {
        try {
            return configRepository.getBooleanValue("config.security.audit-all-changes", true);
        } catch (Exception e) {
            logger.warn("Error reading audit-all-changes configuration, using default: {}", e.getMessage());
            return true;
        }
    }
    
    private int getMaxFailedAttempts() {
        try {
            return configRepository.getIntegerValue("config.security.max-failed-attempts", 5);
        } catch (Exception e) {
            logger.warn("Error reading max-failed-attempts configuration, using default: {}", e.getMessage());
            return 5;
        }
    }
    
    private int getLockoutDurationMinutes() {
        try {
            return configRepository.getIntegerValue("config.security.lockout-duration-minutes", 30);
        } catch (Exception e) {
            logger.warn("Error reading lockout-duration-minutes configuration, using default: {}", e.getMessage());
            return 30;
        }
    }
}