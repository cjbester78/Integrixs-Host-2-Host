package com.integrixs.shared.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class for audit-related operations across the application
 * Provides centralized methods for getting current user information for audit trails
 */
@Component
public class AuditUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditUtils.class);
    
    private static JdbcTemplate jdbcTemplate;
    
    @Autowired
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        AuditUtils.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Get current logged-in user ID for audit fields (created_by, updated_by)
     * This method should be used by all repositories for consistent audit tracking
     * 
     * @return Current user ID as string, or "system" if no authenticated user
     */
    public static String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
                Object principal = authentication.getPrincipal();
                
                // Try to get ID using reflection for models that have getId method
                try {
                    java.lang.reflect.Method getIdMethod = principal.getClass().getMethod("getId");
                    Object userId = getIdMethod.invoke(principal);
                    return userId != null ? userId.toString() : "system";
                } catch (Exception e) {
                    // Fall back to username if getId method doesn't exist
                    if (principal instanceof UserDetails) {
                        return ((UserDetails) principal).getUsername();
                    }
                }
            }
            // fallback for system operations - return configured system integration user ID
            String systemUserId = getSystemUserId();
            return systemUserId != null ? systemUserId : "system";
        } catch (Exception e) {
            logger.warn("Unable to get current user ID for audit: {}", e.getMessage());
            // fallback for system operations - return configured system integration user ID
            String systemUserId = getSystemUserId();
            return systemUserId != null ? systemUserId : "system";
        }
    }
    
    /**
     * Get current logged-in user for audit purposes
     * 
     * @return Current User object, or null if no authenticated user
     */
    public static Object getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                return authentication.getPrincipal();
            }
            return null;
        } catch (Exception e) {
            logger.warn("Unable to get current user for audit: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Get current logged-in username for audit purposes
     * 
     * @return Current username as string, or "system" if no authenticated user
     */
    public static String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                return userDetails.getUsername();
            }
            return "system";
        } catch (Exception e) {
            logger.warn("Unable to get current username for audit: {}", e.getMessage());
            return "system";
        }
    }
    
    /**
     * Get system user ID for automated operations
     * Returns the configured system integration user ID from system configuration
     * 
     * @return System integration user UUID as string, or null if not found
     */
    public static String getSystemUserId() {
        try {
            if (jdbcTemplate != null) {
                // Get the configured system integration username from system configuration
                String getConfigSql = "SELECT config_value FROM system_configuration WHERE config_key = 'system.integration.username'";
                String systemUsername = jdbcTemplate.queryForObject(getConfigSql, String.class);
                
                if (systemUsername != null) {
                    // Get the user ID for the configured system integration user
                    String getUserSql = "SELECT id FROM users WHERE username = ? LIMIT 1";
                    return jdbcTemplate.queryForObject(getUserSql, String.class, systemUsername);
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to get system integration user ID from configuration: {}", e.getMessage());
        }
        return null;
    }
}