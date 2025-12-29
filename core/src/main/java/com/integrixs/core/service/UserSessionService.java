package com.integrixs.core.service;

import com.integrixs.core.repository.UserSessionRepository;
import com.integrixs.shared.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user sessions and security monitoring
 * Provides session tracking, validation, and cleanup operations
 */
@Service
@Transactional
public class UserSessionService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserSessionService.class);
    private final UserSessionRepository repository;
    
    public UserSessionService(UserSessionRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Create a new user session
     */
    public UserSession createSession(UUID userId, String sessionId, String ipAddress, String userAgent, LocalDateTime expiresAt) {
        try {
            UserSession session = new UserSession(userId, sessionId, ipAddress, userAgent, expiresAt);
            return repository.save(session);
        } catch (Exception e) {
            logger.error("Failed to create user session for user: {} - {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Create a new user session with login log correlation
     */
    public UserSession createSession(UUID userId, String sessionId, String ipAddress, String userAgent, 
                                   LocalDateTime expiresAt, UUID loginLogId) {
        try {
            UserSession session = new UserSession(userId, sessionId, ipAddress, userAgent, expiresAt);
            session.setLoginLogId(loginLogId);
            return repository.save(session);
        } catch (Exception e) {
            logger.error("Failed to create user session with log correlation for user: {} - {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Find and validate an active session
     */
    public Optional<UserSession> findActiveSession(String sessionId) {
        try {
            Optional<UserSession> sessionOpt = repository.findBySessionId(sessionId);
            
            if (sessionOpt.isPresent()) {
                UserSession session = sessionOpt.get();
                
                // Check if session is expired
                if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
                    deactivateSession(sessionId);
                    return Optional.empty();
                }
                
                return Optional.of(session);
            }
            
            return Optional.empty();
        } catch (Exception e) {
            logger.error("Failed to find active session: {} - {}", sessionId, e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    /**
     * Get all active sessions for a user
     */
    public List<UserSession> getActiveUserSessions(UUID userId) {
        try {
            return repository.findActiveSessionsByUserId(userId);
        } catch (Exception e) {
            logger.error("Failed to get active sessions for user: {} - {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get session history for a user
     */
    public List<UserSession> getUserSessionHistory(UUID userId, int limit) {
        try {
            return repository.findByUserIdOrderByCreatedAtDesc(userId, limit);
        } catch (Exception e) {
            logger.error("Failed to get session history for user: {} - {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get sessions from IP address within time range
     */
    public List<UserSession> getSessionsByIpAddress(String ipAddress, LocalDateTime since) {
        try {
            return repository.findByIpAddressSince(ipAddress, since);
        } catch (Exception e) {
            logger.error("Failed to get sessions for IP: {} - {}", ipAddress, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Check if user has reached maximum concurrent sessions
     */
    public boolean hasExceededMaxSessions(UUID userId, int maxSessions) {
        try {
            long activeSessions = repository.countActiveSessionsByUserId(userId);
            return activeSessions >= maxSessions;
        } catch (Exception e) {
            logger.error("Failed to check max sessions for user: {} - {}", userId, e.getMessage(), e);
            return false; // Fail safe - allow login
        }
    }
    
    /**
     * Deactivate a specific session
     */
    public void deactivateSession(String sessionId) {
        try {
            repository.deactivateSession(sessionId);
            logger.info("Deactivated session: {}", sessionId);
        } catch (Exception e) {
            logger.error("Failed to deactivate session: {} - {}", sessionId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Deactivate all sessions for a user (e.g., on password change)
     */
    public void deactivateAllUserSessions(UUID userId) {
        try {
            repository.deactivateAllUserSessions(userId);
            logger.info("Deactivated all sessions for user: {}", userId);
        } catch (Exception e) {
            logger.error("Failed to deactivate all sessions for user: {} - {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        try {
            List<UserSession> expiredSessions = repository.findExpiredSessions();
            if (!expiredSessions.isEmpty()) {
                logger.info("Cleaning up {} expired sessions", expiredSessions.size());
                repository.cleanupExpiredSessions();
            }
        } catch (Exception e) {
            logger.error("Failed to cleanup expired sessions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Delete old inactive sessions for data retention
     */
    public void deleteOldSessions(int retentionDays) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            repository.deleteOldInactiveSessions(cutoffDate);
            logger.info("Deleted inactive sessions older than {} days", retentionDays);
        } catch (Exception e) {
            logger.error("Failed to delete old sessions: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get session statistics for monitoring
     */
    public List<Object[]> getSessionStatistics(int hours) {
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(hours);
            return repository.getSessionStatistics(since);
        } catch (Exception e) {
            logger.error("Failed to get session statistics: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Check for suspicious activity based on IP address
     */
    public boolean isSuspiciousIpActivity(String ipAddress, int maxSessionsPerHour) {
        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            List<UserSession> recentSessions = repository.findByIpAddressSince(ipAddress, oneHourAgo);
            return recentSessions.size() > maxSessionsPerHour;
        } catch (Exception e) {
            logger.error("Failed to check suspicious IP activity: {} - {}", ipAddress, e.getMessage(), e);
            return false; // Fail safe
        }
    }
}