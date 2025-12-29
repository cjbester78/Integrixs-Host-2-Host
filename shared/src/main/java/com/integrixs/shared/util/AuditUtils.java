package com.integrixs.shared.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Utility class for audit-related operations across the application
 * Provides centralized methods for getting current user information for audit trails
 */
public class AuditUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditUtils.class);
    
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
            return "system"; // fallback for system operations like initialization
        } catch (Exception e) {
            logger.warn("Unable to get current user ID for audit: {}", e.getMessage());
            return "system";
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
}