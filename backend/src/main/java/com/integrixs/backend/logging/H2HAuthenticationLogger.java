package com.integrixs.backend.logging;

import com.integrixs.core.service.TransactionLogService;
import com.integrixs.core.service.UserSessionService;
import com.integrixs.core.service.UserManagementErrorService;
import com.integrixs.core.service.ThreatAssessmentService;
import com.integrixs.shared.model.TransactionLog;
import com.integrixs.shared.model.UserManagementError;
import com.integrixs.shared.model.UserSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * H2H Authentication Logger
 * Implements two-tier logging: APPLICATION logs (files) and TRANSACTIONAL logs (files + database)
 * Enhanced with Phase 3 enterprise security features: user session tracking and error recording
 * 
 * This component handles all authentication events in H2H:
 * - Authentication attempts
 * - Authentication successes with session creation
 * - Authentication failures with threat assessment
 * - User logouts with session management
 * - Security error tracking and threat analysis
 */
@Component
public class H2HAuthenticationLogger {
    
    private static final Logger logger = LoggerFactory.getLogger(H2HAuthenticationLogger.class);
    
    private final TransactionLogService transactionLogService;
    private final UserSessionService userSessionService;
    private final UserManagementErrorService errorService;
    private final ThreatAssessmentService threatAssessmentService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public H2HAuthenticationLogger(TransactionLogService transactionLogService,
                                 UserSessionService userSessionService,
                                 UserManagementErrorService errorService,
                                 ThreatAssessmentService threatAssessmentService) {
        this.transactionLogService = transactionLogService;
        this.userSessionService = userSessionService;
        this.errorService = errorService;
        this.threatAssessmentService = threatAssessmentService;
    }
    
    /**
     * Log authentication attempt
     * Called when a user tries to log in (before validation)
     */
    public void logAuthenticationAttempt(String username, String authMethod, String ipAddress, String userAgent) {
        // File logging (APPLICATION) - Human-readable for developers
        logger.info("AUTHENTICATION.ATTEMPT - User: {} - Method: {} - IP: {} - UserAgent: {}", 
                   username, authMethod, ipAddress, trimUserAgent(userAgent));
        
        // Database logging (TRANSACTIONAL) - Structured for security monitoring
        try {
            TransactionLog log = transactionLogService.createAuthenticationLog(
                "AUTH_ATTEMPT",
                "Authentication attempt for user: " + username,
                username,
                ipAddress,
                userAgent,
                null // No session ID yet
            );
            
            // Add additional context details
            Map<String, Object> details = new HashMap<>();
            details.put("authMethod", authMethod);
            details.put("thread", Thread.currentThread().getName());
            details.put("eventTime", System.currentTimeMillis());
            log.setDetails(objectMapper.writeValueAsString(details));
            
            transactionLogService.log(log);
            
        } catch (Exception e) {
            logger.error("Failed to log authentication attempt to database for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
    }
    
    /**
     * Log successful authentication with enhanced session tracking
     * Called when authentication succeeds and JWT token is generated
     */
    public void logAuthenticationSuccess(String username, String sessionId, String ipAddress, 
                                       String userAgent, String roles, String tokenExpiry, UUID userId) {
        // File logging (APPLICATION) - Human-readable
        logger.info("AUTHENTICATION.SUCCESS - User: {} - Session: {} - IP: {} - Roles: {} - TokenExpiry: {}", 
                   username, sessionId, ipAddress, roles, tokenExpiry);
        
        // Database logging (TRANSACTIONAL) - Structured
        TransactionLog authLog = null;
        try {
            authLog = transactionLogService.createAuthenticationLog(
                "AUTH_SUCCESS",
                "Authentication successful for user: " + username,
                username,
                ipAddress,
                userAgent,
                sessionId
            );
            
            // Add success context details
            Map<String, Object> details = new HashMap<>();
            details.put("roles", roles);
            details.put("authenticated", true);
            details.put("tokenExpiry", tokenExpiry);
            details.put("authType", "JWT");
            details.put("loginTime", System.currentTimeMillis());
            authLog.setDetails(objectMapper.writeValueAsString(details));
            
            authLog = transactionLogService.log(authLog);
            
        } catch (Exception e) {
            logger.error("Failed to log authentication success to database for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
        
        // Phase 3 Enhancement: Create user session tracking
        try {
            if (userId != null && sessionId != null) {
                // Parse token expiry to LocalDateTime
                LocalDateTime expiresAt = parseTokenExpiry(tokenExpiry);
                
                // Create user session with login log correlation
                UserSession session = userSessionService.createSession(
                    userId, sessionId, ipAddress, userAgent, expiresAt, 
                    authLog != null ? authLog.getId() : null
                );
                
                logger.debug("Created user session: {} for user: {} expires: {}", 
                           sessionId, username, expiresAt);
            }
        } catch (Exception e) {
            logger.error("Failed to create user session for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
    }
    
    /**
     * Log successful authentication (backward compatibility)
     * Called when authentication succeeds and JWT token is generated
     */
    public void logAuthenticationSuccess(String username, String sessionId, String ipAddress, 
                                       String userAgent, String roles, String tokenExpiry) {
        // Call enhanced method with null userId (will skip session tracking)
        logAuthenticationSuccess(username, sessionId, ipAddress, userAgent, roles, tokenExpiry, null);
    }
    
    /**
     * Log authentication failure with enhanced threat assessment
     * Called when authentication fails (wrong password, locked account, etc.)
     */
    public void logAuthenticationFailure(String username, String failureReason, String authMethod, 
                                       String ipAddress, String userAgent) {
        // File logging (APPLICATION) - Human-readable with warning level
        logger.warn("AUTHENTICATION.FAILED - User: {} - Reason: {} - Method: {} - IP: {} - UserAgent: {}", 
                   username != null ? username : "UNKNOWN", failureReason, authMethod, ipAddress, trimUserAgent(userAgent));
        
        // Phase 3 Enhancement: Threat Assessment
        UserManagementError.ThreatLevel threatLevel = UserManagementError.ThreatLevel.LOW;
        try {
            threatLevel = threatAssessmentService.assessAuthenticationThreat(
                ipAddress, username, failureReason, userAgent);
        } catch (Exception e) {
            logger.debug("Failed to assess threat level: {}", e.getMessage());
        }
        
        // Database logging (TRANSACTIONAL) - Structured with ERROR level
        TransactionLog authLog = null;
        try {
            authLog = transactionLogService.createAuthenticationLog(
                "AUTH_FAILED",
                "Authentication failed for user: " + (username != null ? username : "UNKNOWN") + " - " + failureReason,
                username,
                ipAddress,
                userAgent,
                null // No session ID for failed attempts
            );
            
            authLog.setLevel(TransactionLog.LogLevel.ERROR);
            
            // Add failure context details with enhanced security analysis
            Map<String, Object> details = new HashMap<>();
            details.put("authMethod", authMethod);
            details.put("failureReason", failureReason);
            details.put("failureTime", System.currentTimeMillis());
            details.put("thread", Thread.currentThread().getName());
            details.put("threatLevel", threatLevel.name());
            
            // Enhanced security analysis flags
            details.put("suspiciousActivity", isSuspiciousActivity(ipAddress, username));
            details.put("repeatedFailure", isRepeatedFailure(ipAddress, username));
            details.put("suspiciousUserAgent", threatAssessmentService.isSuspiciousUserAgent(userAgent));
            details.put("knownMaliciousIp", threatAssessmentService.isKnownMaliciousIp(ipAddress));
            
            authLog.setDetails(objectMapper.writeValueAsString(details));
            
            authLog = transactionLogService.log(authLog);
            
        } catch (Exception e) {
            logger.error("Failed to log authentication failure to database for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
        
        // Phase 3 Enhancement: Record security error with threat assessment
        try {
            UserManagementError error = errorService.recordAuthenticationError(
                "LOGIN_FAILED",
                "login",
                failureReason,
                username,
                ipAddress,
                userAgent,
                authLog != null ? authLog.getId() : null
            );
            
            // Set assessed threat level
            error.setThreatLevel(threatLevel);
            
            if (threatLevel.ordinal() >= UserManagementError.ThreatLevel.HIGH.ordinal()) {
                logger.warn("HIGH/CRITICAL authentication threat detected - User: {} IP: {} ThreatLevel: {}", 
                           username, ipAddress, threatLevel);
            }
            
        } catch (Exception e) {
            logger.error("Failed to record authentication error for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
    }
    
    /**
     * Log user logout with enhanced session management
     * Called when user explicitly logs out or token is invalidated
     */
    public void logLogout(String username, String sessionId, String ipAddress, String reason) {
        // File logging (APPLICATION) - Human-readable
        logger.info("LOGOUT - User: {} - Session: {} - IP: {} - Reason: {}", 
                   username, sessionId, ipAddress, reason != null ? reason : "User initiated");
        
        // Database logging (TRANSACTIONAL) - Structured
        try {
            TransactionLog log = transactionLogService.createAuthenticationLog(
                "LOGOUT",
                "User logout: " + username + (reason != null ? " (" + reason + ")" : ""),
                username,
                ipAddress,
                null, // User agent not always available during logout
                sessionId
            );
            
            // Add logout context details
            Map<String, Object> details = new HashMap<>();
            details.put("logoutReason", reason != null ? reason : "User initiated");
            details.put("logoutTime", System.currentTimeMillis());
            log.setDetails(objectMapper.writeValueAsString(details));
            
            transactionLogService.log(log);
            
        } catch (Exception e) {
            logger.error("Failed to log logout to database for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
        
        // Phase 3 Enhancement: Deactivate user session
        try {
            if (sessionId != null) {
                userSessionService.deactivateSession(sessionId);
                logger.debug("Deactivated session: {} for user: {}", sessionId, username);
            }
        } catch (Exception e) {
            logger.error("Failed to deactivate session for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
    }
    
    /**
     * Log session timeout
     * Called when a user session expires
     */
    public void logSessionTimeout(String username, String sessionId, String ipAddress) {
        // File logging (APPLICATION)
        logger.info("SESSION.TIMEOUT - User: {} - Session: {} - IP: {}", username, sessionId, ipAddress);
        
        // Database logging (TRANSACTIONAL)
        try {
            TransactionLog log = transactionLogService.createAuthenticationLog(
                "LOGOUT",
                "Session timeout for user: " + username,
                username,
                ipAddress,
                null,
                sessionId
            );
            
            Map<String, Object> details = new HashMap<>();
            details.put("logoutReason", "Session timeout");
            details.put("timeoutTime", System.currentTimeMillis());
            log.setDetails(objectMapper.writeValueAsString(details));
            
            transactionLogService.log(log);
            
        } catch (Exception e) {
            logger.error("Failed to log session timeout to database for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
        
        // Phase 3 Enhancement: Deactivate expired session
        try {
            if (sessionId != null) {
                userSessionService.deactivateSession(sessionId);
                logger.debug("Deactivated expired session: {} for user: {}", sessionId, username);
            }
        } catch (Exception e) {
            logger.error("Failed to deactivate expired session for user: {} - Error: {}", 
                        username, e.getMessage(), e);
        }
    }
    
    // Helper methods
    
    /**
     * Trim user agent string for cleaner file logs
     */
    private String trimUserAgent(String userAgent) {
        if (userAgent == null) return "Unknown";
        if (userAgent.length() > 100) {
            return userAgent.substring(0, 100) + "...";
        }
        return userAgent;
    }
    
    /**
     * Check if this represents suspicious authentication activity
     * (This is a simple implementation - can be enhanced with more sophisticated logic)
     */
    private boolean isSuspiciousActivity(String ipAddress, String username) {
        try {
            if (ipAddress == null) return false;
            
            // Check recent failure count from this IP
            long recentFailures = transactionLogService.countAuthenticationFailuresByIp(
                ipAddress, java.time.Duration.ofMinutes(15));
            
            // Consider suspicious if more than 3 failures in 15 minutes
            return recentFailures >= 3;
            
        } catch (Exception e) {
            logger.debug("Error checking suspicious activity: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if this is a repeated failure for the same IP/username combination
     */
    private boolean isRepeatedFailure(String ipAddress, String username) {
        try {
            if (ipAddress == null) return false;
            
            // Check recent failures from this IP
            long ipFailures = transactionLogService.countAuthenticationFailuresByIp(
                ipAddress, java.time.Duration.ofMinutes(5));
            
            // Check recent failures for this username (if provided)
            long usernameFailures = 0;
            if (username != null) {
                usernameFailures = transactionLogService.countAuthenticationFailuresByUsername(
                    username, java.time.Duration.ofMinutes(5));
            }
            
            // Consider repeated if more than 1 failure in 5 minutes
            return ipFailures > 1 || usernameFailures > 1;
            
        } catch (Exception e) {
            logger.debug("Error checking repeated failure: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Parse token expiry string to LocalDateTime
     * Expected format examples: "2024-12-12T18:30:00" or milliseconds timestamp
     */
    private LocalDateTime parseTokenExpiry(String tokenExpiry) {
        if (tokenExpiry == null || tokenExpiry.trim().isEmpty()) {
            // Default to 24 hours from now
            return LocalDateTime.now().plusHours(24);
        }
        
        try {
            // Try parsing as ISO date-time
            if (tokenExpiry.contains("T")) {
                return LocalDateTime.parse(tokenExpiry, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            
            // Try parsing as milliseconds timestamp
            if (tokenExpiry.matches("\\d+")) {
                long timestamp = Long.parseLong(tokenExpiry);
                return LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    java.time.ZoneId.systemDefault()
                );
            }
            
            // Fallback: assume it's in minutes from now
            int minutes = Integer.parseInt(tokenExpiry);
            return LocalDateTime.now().plusMinutes(minutes);
            
        } catch (Exception e) {
            logger.debug("Failed to parse token expiry '{}', using default 24 hours: {}", tokenExpiry, e.getMessage());
            return LocalDateTime.now().plusHours(24);
        }
    }
}