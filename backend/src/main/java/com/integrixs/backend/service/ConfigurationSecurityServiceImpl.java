package com.integrixs.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Implementation of Configuration Security Service
 * Provides comprehensive security validation and protection for configuration operations
 */
@Service
public class ConfigurationSecurityServiceImpl implements ConfigurationSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationSecurityServiceImpl.class);
    private static final Logger securityLogger = LoggerFactory.getLogger("CONFIG_SECURITY");
    
    private final SystemConfigurationService systemConfigurationService;
    private final SecurityAuditService securityAuditService;
    
    // Security policies - in production this would be loaded from database
    private final Map<String, ConfigurationSecurityPolicy> securityPolicies;
    private final Map<String, List<ConfigurationAccessAttempt>> userAccessHistory;
    private final ConfigurationEncryption encryption;

    public ConfigurationSecurityServiceImpl(SystemConfigurationService systemConfigurationService,
                                          SecurityAuditService securityAuditService) {
        this.systemConfigurationService = systemConfigurationService;
        this.securityAuditService = securityAuditService;
        this.securityPolicies = initializeSecurityPolicies();
        this.userAccessHistory = new ConcurrentHashMap<>();
        this.encryption = new ConfigurationEncryption();
    }

    @Override
    public ConfigurationAuthorizationResult authorizeAccess(String userId, String configKey, ConfigurationOperation operation) {
        try {
            // Find applicable security policy
            ConfigurationSecurityPolicy policy = findApplicablePolicy(configKey);
            
            if (policy == null) {
                // Default policy for unconfigured keys
                if (operation == ConfigurationOperation.READ) {
                    return ConfigurationAuthorizationResult.createAuthorized();
                } else {
                    return ConfigurationAuthorizationResult.denied("No policy defined for key", 
                        List.of("ADMINISTRATOR"));
                }
            }
            
            // Check role-based access
            // In a real implementation, you would get user roles from UserService
            List<String> userRoles = getUserRoles(userId); // Simplified for example
            
            boolean hasRequiredRole = userRoles.stream()
                .anyMatch(role -> policy.allowedRoles().contains(role));
            
            if (!hasRequiredRole) {
                return ConfigurationAuthorizationResult.denied("Insufficient role", policy.allowedRoles());
            }
            
            // Check security level requirements
            SecurityLevel userSecurityLevel = getUserSecurityLevel(userId);
            if (userSecurityLevel.ordinal() < policy.requiredSecurityLevel().ordinal()) {
                return ConfigurationAuthorizationResult.deniedSecurityLevel(policy.requiredSecurityLevel());
            }
            
            return ConfigurationAuthorizationResult.createAuthorized();
            
        } catch (Exception e) {
            logger.error("Error during configuration authorization for user {} and key {}", userId, configKey, e);
            return ConfigurationAuthorizationResult.denied("Authorization check failed", List.of());
        }
    }

    @Override
    public ConfigurationSecurityValidationResult validateConfigurationSecurity(String configKey, String newValue, String userId) {
        List<String> concerns = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        SecurityRiskLevel riskLevel = SecurityRiskLevel.LOW;
        
        // Check for sensitive data patterns
        if (containsSensitivePatterns(newValue)) {
            concerns.add("Value contains potentially sensitive data patterns");
            recommendations.add("Consider encrypting this value");
            riskLevel = SecurityRiskLevel.MEDIUM;
        }
        
        // Check for security configuration changes
        if (isSecurityConfiguration(configKey)) {
            concerns.add("Security-related configuration change");
            recommendations.add("Additional security review recommended");
            riskLevel = SecurityRiskLevel.HIGH;
        }
        
        // Check for suspicious values
        if (containsSuspiciousPatterns(newValue)) {
            concerns.add("Value contains suspicious patterns");
            recommendations.add("Manual review required");
            riskLevel = SecurityRiskLevel.HIGH;
        }
        
        // Check for system-critical configurations
        if (isSystemCriticalConfiguration(configKey)) {
            concerns.add("System-critical configuration change");
            recommendations.add("Backup current configuration before applying");
            riskLevel = SecurityRiskLevel.CRITICAL;
        }
        
        boolean secure = concerns.isEmpty();
        return secure ? 
            ConfigurationSecurityValidationResult.createSecure() :
            ConfigurationSecurityValidationResult.insecure(concerns, riskLevel, recommendations);
    }

    @Override
    public String encryptSensitiveValue(String key, String value) {
        if (!isSensitiveConfiguration(key) || value == null) {
            return value;
        }
        
        try {
            return encryption.encrypt(value);
        } catch (Exception e) {
            logger.error("Failed to encrypt sensitive value for key: {}", key, e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public Optional<String> decryptSensitiveValue(String key, String encryptedValue) {
        if (!isSensitiveConfiguration(key) || encryptedValue == null) {
            return Optional.of(encryptedValue);
        }
        
        try {
            String decryptedValue = encryption.decrypt(encryptedValue);
            return Optional.of(decryptedValue);
        } catch (Exception e) {
            logger.error("Failed to decrypt sensitive value for key: {}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isSensitiveConfiguration(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") ||
               lowerKey.contains("secret") ||
               lowerKey.contains("key") ||
               lowerKey.contains("token") ||
               lowerKey.contains("credential") ||
               lowerKey.contains("private") ||
               lowerKey.startsWith("security.");
    }

    @Override
    public String getMaskedValue(String key, String value) {
        if (value == null) return null;
        
        if (isSensitiveConfiguration(key)) {
            return "*".repeat(Math.min(value.length(), 12));
        }
        
        return value;
    }

    @Override
    public void auditConfigurationAccess(String userId, String configKey, ConfigurationOperation operation, boolean authorized) {
        // Record access attempt
        ConfigurationAccessAttempt attempt = new ConfigurationAccessAttempt(
            userId, configKey, operation, authorized, 
            authorized ? null : "Unauthorized access", 
            LocalDateTime.now(), Map.of()
        );
        
        userAccessHistory.computeIfAbsent(userId, k -> new ArrayList<>()).add(attempt);
        
        // Log to security audit service
        SecurityAuditService.ConfigurationSecurityEvent auditEvent = authorized ?
            SecurityAuditService.ConfigurationSecurityEvent.authorized(userId, configKey, 
                mapToAuditOperation(operation), null, null) :
            SecurityAuditService.ConfigurationSecurityEvent.unauthorized(userId, configKey,
                mapToAuditOperation(operation), "Access denied by security policy");
        
        securityAuditService.logConfigurationSecurityEvent(auditEvent);
        
        // Clean up old history (keep last 100 attempts per user)
        List<ConfigurationAccessAttempt> history = userAccessHistory.get(userId);
        if (history.size() > 100) {
            history.subList(0, history.size() - 100).clear();
        }
    }

    @Override
    public List<ConfigurationSecurityPolicy> getSecurityPolicies() {
        return new ArrayList<>(securityPolicies.values());
    }

    @Override
    public SuspiciousConfigurationReport checkSuspiciousActivity(String userId, List<ConfigurationAccessAttempt> recentAttempts) {
        List<SuspiciousPattern> patterns = new ArrayList<>();
        int suspicionScore = 0;
        
        // Check for rapid configuration changes
        long recentChanges = recentAttempts.stream()
            .filter(attempt -> attempt.timestamp().isAfter(LocalDateTime.now().minusMinutes(15)))
            .filter(attempt -> attempt.operation() != ConfigurationOperation.READ)
            .count();
        
        if (recentChanges >= 10) {
            patterns.add(new SuspiciousPattern(
                SuspiciousPatternType.RAPID_CONFIGURATION_CHANGES,
                "More than 10 configuration changes in 15 minutes",
                (int) recentChanges, 25
            ));
            suspicionScore += 25;
        }
        
        // Check for sensitive data access patterns
        long sensitiveAccess = recentAttempts.stream()
            .filter(attempt -> isSensitiveConfiguration(attempt.configKey()))
            .count();
        
        if (sensitiveAccess >= 5) {
            patterns.add(new SuspiciousPattern(
                SuspiciousPatternType.SENSITIVE_DATA_ACCESS_PATTERN,
                "Multiple sensitive configuration access attempts",
                (int) sensitiveAccess, 20
            ));
            suspicionScore += 20;
        }
        
        // Check for unusual time access
        long offHoursAccess = recentAttempts.stream()
            .filter(attempt -> {
                int hour = attempt.timestamp().getHour();
                return hour < 6 || hour > 22;
            })
            .count();
        
        if (offHoursAccess > 0) {
            patterns.add(new SuspiciousPattern(
                SuspiciousPatternType.UNUSUAL_TIME_ACCESS,
                "Configuration access during off hours",
                (int) offHoursAccess, 15
            ));
            suspicionScore += 15;
        }
        
        // Check for failed authorization attempts
        long failedAttempts = recentAttempts.stream()
            .filter(attempt -> !attempt.successful())
            .count();
        
        if (failedAttempts >= 3) {
            patterns.add(new SuspiciousPattern(
                SuspiciousPatternType.UNAUTHORIZED_SYSTEM_CONFIG_ACCESS,
                "Multiple unauthorized configuration access attempts",
                (int) failedAttempts, 30
            ));
            suspicionScore += 30;
        }
        
        // Cap suspicion score
        suspicionScore = Math.min(100, suspicionScore);
        
        String recommendation = generateRecommendation(suspicionScore, patterns);
        
        return new SuspiciousConfigurationReport(
            userId, suspicionScore > 0, suspicionScore, patterns,
            recentAttempts.size(), LocalDateTime.now(), recommendation
        );
    }

    // Private helper methods

    private Map<String, ConfigurationSecurityPolicy> initializeSecurityPolicies() {
        Map<String, ConfigurationSecurityPolicy> policies = new HashMap<>();
        
        // Security configuration policy
        policies.put("security.*", new ConfigurationSecurityPolicy(
            "security.*", List.of("ADMINISTRATOR"), SecurityLevel.ADMINISTRATOR,
            true, true, List.of("encrypt_sensitive", "audit_all_access")
        ));
        
        // Database configuration policy
        policies.put("database.*", new ConfigurationSecurityPolicy(
            "database.*", List.of("ADMINISTRATOR"), SecurityLevel.ELEVATED,
            true, true, List.of("validate_connection_string", "encrypt_credentials")
        ));
        
        // System configuration policy
        policies.put("system.*", new ConfigurationSecurityPolicy(
            "system.*", List.of("ADMINISTRATOR"), SecurityLevel.ELEVATED,
            false, true, List.of("validate_system_paths")
        ));
        
        // Application configuration policy
        policies.put("app.*", new ConfigurationSecurityPolicy(
            "app.*", List.of("ADMINISTRATOR", "INTEGRATOR"), SecurityLevel.STANDARD,
            false, false, List.of()
        ));
        
        return policies;
    }

    private ConfigurationSecurityPolicy findApplicablePolicy(String configKey) {
        return securityPolicies.entrySet().stream()
            .filter(entry -> configKey.matches(entry.getKey().replace("*", ".*")))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private List<String> getUserRoles(String userId) {
        // Simplified - in real implementation, get from UserService
        return List.of("ADMINISTRATOR"); // Default for example
    }

    private SecurityLevel getUserSecurityLevel(String userId) {
        // Simplified - in real implementation, determine based on user context
        return SecurityLevel.ADMINISTRATOR; // Default for example
    }

    private boolean containsSensitivePatterns(String value) {
        if (value == null) return false;
        
        String lowerValue = value.toLowerCase();
        return lowerValue.contains("password") ||
               lowerValue.contains("secret") ||
               lowerValue.matches(".*\\b[A-Za-z0-9]{32,}\\b.*") || // Long random strings
               lowerValue.matches(".*\\b[A-Za-z0-9+/]{20,}={0,2}\\b.*"); // Base64 patterns
    }

    private boolean containsSuspiciousPatterns(String value) {
        if (value == null) return false;
        
        String lowerValue = value.toLowerCase();
        return lowerValue.contains("../") ||
               lowerValue.contains("..\\") ||
               lowerValue.contains("drop table") ||
               lowerValue.contains("delete from") ||
               lowerValue.contains("<script") ||
               lowerValue.contains("javascript:");
    }

    private boolean isSecurityConfiguration(String key) {
        return key.startsWith("security.") ||
               key.contains("auth") ||
               key.contains("jwt") ||
               key.contains("cors");
    }

    private boolean isSystemCriticalConfiguration(String key) {
        return key.startsWith("system.") ||
               key.contains("database") ||
               key.contains("server.port") ||
               key.contains("ssl");
    }

    private SecurityAuditService.ConfigurationOperation mapToAuditOperation(ConfigurationOperation operation) {
        return switch (operation) {
            case READ -> SecurityAuditService.ConfigurationOperation.READ;
            case CREATE -> SecurityAuditService.ConfigurationOperation.CREATE;
            case UPDATE -> SecurityAuditService.ConfigurationOperation.UPDATE;
            case DELETE -> SecurityAuditService.ConfigurationOperation.DELETE;
            default -> SecurityAuditService.ConfigurationOperation.UPDATE;
        };
    }

    private String generateRecommendation(int suspicionScore, List<SuspiciousPattern> patterns) {
        if (suspicionScore == 0) {
            return "No suspicious activity detected";
        }
        
        if (suspicionScore >= 85) {
            return "High risk: Immediate investigation required. Consider temporarily restricting user configuration access.";
        } else if (suspicionScore >= 70) {
            return "Medium-high risk: Investigation recommended. Review user activity and configuration changes.";
        } else if (suspicionScore >= 50) {
            return "Medium risk: Monitor user activity. Review recent configuration changes.";
        } else {
            return "Low risk: Monitor for continued patterns.";
        }
    }

    // Inner class for encryption operations
    private static class ConfigurationEncryption {
        private static final String ALGORITHM = "AES";
        private final SecretKeySpec secretKey;
        
        public ConfigurationEncryption() {
            // In production, use a properly managed encryption key
            String key = "H2HConfigSecretKey2024!";
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
                keyBytes = Arrays.copyOf(keyBytes, 16); // AES-128
                this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize encryption", e);
            }
        }
        
        public String encrypt(String plainText) throws Exception {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        }
        
        public String decrypt(String encryptedText) throws Exception {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, StandardCharsets.UTF_8);
        }
    }
}