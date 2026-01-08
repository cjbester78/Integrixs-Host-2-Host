package com.integrixs.shared.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

/**
 * Backend-specific SecurityContextHelper that properly integrates with Spring Security
 * This class overrides the shared module's fallback implementation
 */
public class SecurityContextHelper {

    private static final Logger logger = LoggerFactory.getLogger(SecurityContextHelper.class);

    /**
     * Get the current authenticated username from Spring Security context
     */
    public static String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof UserDetails) {
                    UserDetails userDetails = (UserDetails) principal;
                    return userDetails.getUsername();
                } else if (principal instanceof String) {
                    return (String) principal;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve current username: {}", e.getMessage());
        }
        return "system"; // fallback for system operations
    }

    /**
     * Get the current authenticated user ID from Spring Security context
     */
    public static UUID getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                
                // Check if principal is our User entity (from JWT authentication)
                if (principal instanceof com.integrixs.backend.model.User) {
                    com.integrixs.backend.model.User user = (com.integrixs.backend.model.User) principal;
                    return user.getId();
                }
                // Check if principal has getId method (system integrator principal)
                else if (hasGetIdMethod(principal)) {
                    try {
                        Object id = principal.getClass().getMethod("getId").invoke(principal);
                        if (id instanceof UUID) {
                            return (UUID) id;
                        }
                    } catch (Exception e) {
                        logger.debug("Could not invoke getId method on principal: {}", e.getMessage());
                    }
                }
                // Check if principal is UserDetails with User entity
                else if (principal instanceof UserDetails) {
                    UserDetails userDetails = (UserDetails) principal;
                    // Try to parse username as UUID (fallback)
                    try {
                        return UUID.fromString(userDetails.getUsername());
                    } catch (IllegalArgumentException e) {
                        logger.warn("Username is not a valid UUID: {}", userDetails.getUsername());
                    }
                } 
                // Handle case where principal is directly a string (user ID)
                else if (principal instanceof String) {
                    try {
                        return UUID.fromString((String) principal);
                    } catch (IllegalArgumentException e) {
                        logger.warn("Principal string is not a valid UUID: {}", principal);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Could not retrieve current user ID: {}", e.getMessage(), e);
        }
        
        // Throw exception with helpful message for system operations
        throw new IllegalStateException("No authenticated user found in security context. User must be logged in. " +
            "For automated flow execution, ensure SystemAuthenticationService.setIntegratorAuthentication() is called first.");
    }

    /**
     * Get the current user ID as string
     */
    public static String getCurrentUserIdAsString() {
        return getCurrentUserId().toString();
    }

    /**
     * Check if there is an authenticated user in the security context
     */
    public static boolean isAuthenticated() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null 
                && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getName());
        } catch (Exception e) {
            logger.debug("Could not check authentication status: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if the current user has the specified role
     */
    public static boolean hasRole(String role) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role) || 
                                         authority.getAuthority().equals(role));
            }
        } catch (Exception e) {
            logger.debug("Could not check role '{}': {}", role, e.getMessage());
        }
        return false;
    }

    /**
     * Check if the current user has any of the specified roles
     */
    public static boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if an object has a getId() method
     */
    private static boolean hasGetIdMethod(Object obj) {
        try {
            return obj.getClass().getMethod("getId") != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}