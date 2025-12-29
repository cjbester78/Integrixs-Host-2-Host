package com.integrixs.core.service;

import com.integrixs.core.repository.UserManagementErrorRepository;
import com.integrixs.shared.model.UserManagementError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing security errors and threat analysis
 * Provides error tracking, threat assessment, and security monitoring
 */
@Service
@Transactional
public class UserManagementErrorService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserManagementErrorService.class);
    private final UserManagementErrorRepository repository;
    
    public UserManagementErrorService(UserManagementErrorRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Record an authentication error
     */
    public UserManagementError recordAuthenticationError(String errorCode, String action, String errorMessage, 
                                                       String username, String ipAddress, String userAgent) {
        try {
            UserManagementError error = new UserManagementError(
                UserManagementError.ErrorType.AUTHENTICATION,
                errorCode,
                action,
                errorMessage,
                username,
                ipAddress
            );
            error.setUserAgent(userAgent);
            
            return repository.save(error);
        } catch (Exception e) {
            logger.error("Failed to record authentication error for user: {} - {}", username, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Record an authentication error with transaction log correlation
     */
    public UserManagementError recordAuthenticationError(String errorCode, String action, String errorMessage, 
                                                       String username, String ipAddress, String userAgent, 
                                                       UUID transactionLogId) {
        try {
            UserManagementError error = new UserManagementError(
                UserManagementError.ErrorType.AUTHENTICATION,
                errorCode,
                action,
                errorMessage,
                username,
                ipAddress
            );
            error.setUserAgent(userAgent);
            error.setTransactionLogId(transactionLogId);
            
            return repository.save(error);
        } catch (Exception e) {
            logger.error("Failed to record authentication error with transaction log for user: {} - {}", username, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Record a session management error
     */
    public UserManagementError recordSessionError(String errorCode, String action, String errorMessage, 
                                                String username, String ipAddress, String userAgent) {
        try {
            UserManagementError error = new UserManagementError(
                UserManagementError.ErrorType.SESSION_MANAGEMENT,
                errorCode,
                action,
                errorMessage,
                username,
                ipAddress
            );
            error.setUserAgent(userAgent);
            
            return repository.save(error);
        } catch (Exception e) {
            logger.error("Failed to record session error for user: {} - {}", username, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Record a user management error
     */
    public UserManagementError recordUserManagementError(String errorCode, String action, String errorMessage, 
                                                        String username, String ipAddress, String userAgent) {
        try {
            UserManagementError error = new UserManagementError(
                UserManagementError.ErrorType.USER_MANAGEMENT,
                errorCode,
                action,
                errorMessage,
                username,
                ipAddress
            );
            error.setUserAgent(userAgent);
            
            return repository.save(error);
        } catch (Exception e) {
            logger.error("Failed to record user management error: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Record an authorization error
     */
    public UserManagementError recordAuthorizationError(String errorCode, String action, String errorMessage, 
                                                       String username, String ipAddress, String userAgent) {
        try {
            UserManagementError error = new UserManagementError(
                UserManagementError.ErrorType.AUTHORIZATION,
                errorCode,
                action,
                errorMessage,
                username,
                ipAddress
            );
            error.setUserAgent(userAgent);
            
            return repository.save(error);
        } catch (Exception e) {
            logger.error("Failed to record authorization error for user: {} - {}", username, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Set threat level for an error
     */
    public void setThreatLevel(UserManagementError error, UserManagementError.ThreatLevel threatLevel) {
        error.setThreatLevel(threatLevel);
        // Update is handled by the repository save method if needed
    }
    
    /**
     * Resolve an error with resolution notes
     */
    public void resolveError(UUID errorId, String resolutionNotes) {
        try {
            repository.resolveError(errorId, resolutionNotes);
            logger.info("Resolved security error: {} with notes: {}", errorId, resolutionNotes);
        } catch (Exception e) {
            logger.error("Failed to resolve error: {} - {}", errorId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get unresolved errors
     */
    public List<UserManagementError> getUnresolvedErrors(int limit) {
        try {
            return repository.findUnresolvedErrors(limit);
        } catch (Exception e) {
            logger.error("Failed to get unresolved errors: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get high and critical threat errors
     */
    public List<UserManagementError> getHighThreatErrors(int hours, int limit) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return repository.findHighThreatErrors(since, limit);
        } catch (Exception e) {
            logger.error("Failed to get high threat errors: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get errors by threat level
     */
    public List<UserManagementError> getErrorsByThreatLevel(UserManagementError.ThreatLevel threatLevel, int limit) {
        try {
            return repository.findByThreatLevel(threatLevel, limit);
        } catch (Exception e) {
            logger.error("Failed to get errors by threat level: {} - {}", threatLevel, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get errors for a specific user
     */
    public List<UserManagementError> getUserErrors(String username, int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return repository.findByUsernameSince(username, since);
        } catch (Exception e) {
            logger.error("Failed to get errors for user: {} - {}", username, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get errors from specific IP address
     */
    public List<UserManagementError> getIpErrors(String ipAddress, int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return repository.findByIpAddressSince(ipAddress, since);
        } catch (Exception e) {
            logger.error("Failed to get errors for IP: {} - {}", ipAddress, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Check if IP address has suspicious error patterns
     */
    public boolean hasSuspiciousErrorPattern(String ipAddress, int maxErrorsPerHour) {
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long errorCount = repository.countErrorsByIpAddress(ipAddress, oneHourAgo);
            return errorCount > maxErrorsPerHour;
        } catch (Exception e) {
            logger.error("Failed to check suspicious error pattern for IP: {} - {}", ipAddress, e.getMessage(), e);
            return false; // Fail safe
        }
    }
    
    /**
     * Check if user has suspicious error patterns
     */
    public boolean hasSuspiciousUserErrorPattern(String username, int maxErrorsPerHour) {
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            long errorCount = repository.countErrorsByUsername(username, oneHourAgo);
            return errorCount > maxErrorsPerHour;
        } catch (Exception e) {
            logger.error("Failed to check suspicious error pattern for user: {} - {}", username, e.getMessage(), e);
            return false; // Fail safe
        }
    }
    
    /**
     * Get error statistics by type
     */
    public List<Object[]> getErrorStatistics(int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return repository.getErrorStatisticsByType(since);
        } catch (Exception e) {
            logger.error("Failed to get error statistics: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get error statistics by IP address
     */
    public List<Object[]> getIpErrorStatistics(int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return repository.getErrorStatisticsByIpAddress(since);
        } catch (Exception e) {
            logger.error("Failed to get IP error statistics: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get authentication failure patterns
     */
    public List<Object[]> getAuthenticationFailurePatterns(int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return repository.getAuthenticationFailurePatterns(since);
        } catch (Exception e) {
            logger.error("Failed to get authentication failure patterns: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Delete old resolved errors for data retention
     */
    public void deleteOldErrors(int retentionDays) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            repository.deleteOldResolvedErrors(cutoffDate);
            logger.info("Deleted resolved errors older than {} days", retentionDays);
        } catch (Exception e) {
            logger.error("Failed to delete old errors: {}", e.getMessage(), e);
        }
    }
}