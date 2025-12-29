package com.integrixs.shared.service;

import com.integrixs.shared.repository.SystemAuditLogRepository;
import com.integrixs.shared.model.SystemAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

/**
 * Service for comprehensive system audit logging
 * 
 * This service ensures that ALL database operations (INSERT, UPDATE, DELETE) 
 * are properly logged to the system_audit_log table for compliance and security tracking.
 */
@Service
public class SystemAuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemAuditService.class);
    
    private final SystemAuditLogRepository auditLogRepository;
    
    public SystemAuditService(SystemAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    /**
     * Log a comprehensive audit event
     */
    public void logEvent(String eventType, String eventCategory, UUID userId, String entityType, 
                        UUID entityId, String description, Map<String, Object> eventDetails,
                        String ipAddress, String userAgent, UUID correlationId, boolean success) {
        
        try {
            SystemAuditLog auditLog = new SystemAuditLog();
            auditLog.setEventType(eventType);
            auditLog.setEventCategory(eventCategory);
            auditLog.setUserId(userId);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setEventDescription(description);
            auditLog.setEventDetails(eventDetails);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setCorrelationId(correlationId);
            auditLog.setSuccess(success);
            
            // Get session ID from current request if available
            try {
                HttpServletRequest request = getCurrentRequest();
                if (request != null) {
                    auditLog.setSessionId(request.getSession().getId());
                }
            } catch (Exception e) {
                logger.debug("Could not retrieve session ID: {}", e.getMessage());
            }
            
            // Persist to database
            auditLogRepository.insertAuditLog(auditLog);
            
            // Also log to application logs for immediate visibility
            logger.info("AUDIT: {} | {} | User: {} | Entity: {}:{} | {} | Success: {}", 
                       eventType, eventCategory, userId, entityType, entityId, description, success);
                       
        } catch (Exception e) {
            // Never let audit logging disrupt main operations
            logger.error("Failed to log audit event: {} - Error: {}", eventType, e.getMessage(), e);
        }
    }
    
    /**
     * Log a database operation (INSERT, UPDATE, DELETE)
     * 
     * This method should be called after every database operation to maintain audit trail
     */
    public void logDatabaseOperation(String operation, String tableName, UUID entityId, 
                                   String entityName, boolean success, String errorMessage) {
        try {
            UUID currentUserId = getCurrentUserId();
            String currentUsername = getCurrentUsername();
            String ipAddress = getCurrentIpAddress();
            String userAgent = getCurrentUserAgent();
            
            String eventType = "DATABASE_" + operation.toUpperCase();
            String eventCategory = "SYSTEM";
            String description = String.format("%s operation on %s%s", 
                operation.toLowerCase(), 
                tableName,
                entityName != null ? " (" + entityName + ")" : ""
            );
            
            if (!success && errorMessage != null) {
                description += " - Error: " + errorMessage;
            }

            // Create audit log with complete user context
            SystemAuditLog auditLog = new SystemAuditLog();
            auditLog.setEventType(eventType);
            auditLog.setEventCategory(eventCategory);
            auditLog.setEventDescription(description);
            auditLog.setUserId(currentUserId);
            auditLog.setUsername(currentUsername);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setResourceType(tableName.toUpperCase());
            auditLog.setResourceId(entityId);
            auditLog.setResourceName(entityName);
            auditLog.setSuccess(success);
            auditLog.setErrorMessage(errorMessage);
            
            // Get session ID from current request if available
            try {
                HttpServletRequest request = getCurrentRequest();
                if (request != null) {
                    auditLog.setSessionId(request.getSession().getId());
                }
            } catch (Exception e) {
                logger.debug("Could not retrieve session ID: {}", e.getMessage());
            }
            
            auditLogRepository.insertAuditLog(auditLog);
                    
        } catch (Exception e) {
            logger.error("Failed to log database operation: {} on {} - Error: {}", 
                        operation, tableName, e.getMessage(), e);
        }
    }
    
    /**
     * Log a user action (login, logout, access)
     */
    public void logUserAction(String action, UUID userId, String username, boolean success, String errorMessage) {
        try {
            String ipAddress = getCurrentIpAddress();
            String userAgent = getCurrentUserAgent();
            
            String description = String.format("User %s %s", 
                username != null ? username : "unknown", 
                action.toLowerCase()
            );
            
            if (!success && errorMessage != null) {
                description += " - " + errorMessage;
            }
            
            logEvent("USER_" + action.toUpperCase(), "AUTHENTICATION", userId, "USER", 
                    userId, description, null, ipAddress, userAgent, null, success);
                    
        } catch (Exception e) {
            logger.error("Failed to log user action: {} for user {} - Error: {}", 
                        action, username, e.getMessage(), e);
        }
    }
    
    /**
     * Get current user ID from security context
     */
    private UUID getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                Object principal = authentication.getPrincipal();
                
                // Try to get ID using reflection for User models that have getId method
                try {
                    java.lang.reflect.Method getIdMethod = principal.getClass().getMethod("getId");
                    Object userId = getIdMethod.invoke(principal);
                    if (userId instanceof UUID) {
                        return (UUID) userId;
                    }
                } catch (Exception e) {
                    // Fall back to username-based approach if needed
                    logger.debug("Could not get UUID ID from principal: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve current user ID: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                return userDetails.getUsername();
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve current username: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get current HTTP request
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get current user's IP address
     */
    private String getCurrentIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve IP address: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get current user agent
     */
    private String getCurrentUserAgent() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                return request.getHeader("User-Agent");
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve user agent: {}", e.getMessage());
        }
        return null;
    }
}