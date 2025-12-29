package com.integrixs.core.service;

import com.integrixs.core.repository.TransactionLogRepository;
import com.integrixs.shared.model.TransactionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing transaction logs
 * Provides high-level operations for authentication and business transaction logging
 */
@Service
@Transactional
public class TransactionLogService {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionLogService.class);
    
    private final TransactionLogRepository repository;
    
    public TransactionLogService(TransactionLogRepository repository) {
        this.repository = repository;
    }
    
    /**
     * Log a transaction event
     * @param transactionLog The transaction log to save
     * @return The saved transaction log
     */
    public TransactionLog log(TransactionLog transactionLog) {
        try {
            if (transactionLog.getTimestamp() == null) {
                transactionLog.setTimestamp(LocalDateTime.now());
            }
            if (transactionLog.getCreatedAt() == null) {
                transactionLog.setCreatedAt(LocalDateTime.now());
            }
            
            TransactionLog saved = repository.save(transactionLog);
            logger.debug("Saved transaction log: {} - {}", saved.getCategory(), saved.getMessage());
            return saved;
        } catch (Exception e) {
            logger.error("Failed to save transaction log to database: {} - Error: {}", 
                        transactionLog.getCategory(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Log a transaction event asynchronously
     * @param transactionLog The transaction log to save
     * @return CompletableFuture with the saved transaction log
     */
    @Async
    public CompletableFuture<TransactionLog> logAsync(TransactionLog transactionLog) {
        try {
            TransactionLog saved = log(transactionLog);
            return CompletableFuture.completedFuture(saved);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Find logs by category with limit
     */
    public List<TransactionLog> findByCategory(String category, int limit) {
        return repository.findByCategoryOrderByTimestampDesc(category, limit);
    }
    
    /**
     * Find logs by username with limit
     */
    public List<TransactionLog> findByUsername(String username, int limit) {
        return repository.findByUsernameOrderByTimestampDesc(username, limit);
    }
    
    /**
     * Find logs by correlation ID (for flow tracking)
     */
    public List<TransactionLog> findByCorrelationId(String correlationId) {
        return repository.findByCorrelationIdOrderByTimestampAsc(correlationId);
    }
    
    /**
     * Find logs by adapter ID
     */
    public List<TransactionLog> findByAdapterId(UUID adapterId, int limit) {
        return repository.findByAdapterIdOrderByTimestampDesc(adapterId, limit);
    }
    
    /**
     * Find logs by execution ID (for execution tracking)
     */
    public List<TransactionLog> findByExecutionId(UUID executionId) {
        return repository.findByExecutionIdOrderByTimestampAsc(executionId);
    }
    
    /**
     * Find recent authentication logs
     */
    public List<TransactionLog> findRecentAuthenticationLogs(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return repository.findRecentAuthenticationLogs(since, limit);
    }
    
    /**
     * Find recent error logs
     */
    public List<TransactionLog> findRecentErrors(int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return repository.findRecentErrors(since, limit);
    }
    
    /**
     * Find authentication failures for a specific IP address
     */
    public List<TransactionLog> findAuthenticationFailuresByIp(String ipAddress, Duration duration) {
        LocalDateTime since = LocalDateTime.now().minus(duration);
        return repository.findAuthenticationFailuresByIp(ipAddress, since);
    }
    
    /**
     * Find authentication failures for a specific username
     */
    public List<TransactionLog> findAuthenticationFailuresByUsername(String username, Duration duration) {
        LocalDateTime since = LocalDateTime.now().minus(duration);
        return repository.findAuthenticationFailuresByUsername(username, since);
    }
    
    /**
     * Count authentication failures for an IP address within a time period
     */
    public long countAuthenticationFailuresByIp(String ipAddress, Duration duration) {
        LocalDateTime since = LocalDateTime.now().minus(duration);
        return repository.countAuthenticationFailuresByIp(ipAddress, since);
    }
    
    /**
     * Count authentication failures for a username within a time period
     */
    public long countAuthenticationFailuresByUsername(String username, Duration duration) {
        LocalDateTime since = LocalDateTime.now().minus(duration);
        return repository.countAuthenticationFailuresByUsername(username, since);
    }
    
    /**
     * Find file processing logs for a specific file
     */
    public List<TransactionLog> findFileProcessingLogs(String fileName) {
        return repository.findFileProcessingLogs(fileName);
    }
    
    /**
     * Get authentication statistics for dashboard
     */
    public Map<String, Long> getAuthenticationStatistics(int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Object[]> results = repository.getAuthenticationStatistics(since);
        
        return results.stream()
                .collect(java.util.stream.Collectors.toMap(
                        result -> (String) result[0],
                        result -> ((Number) result[1]).longValue()
                ));
    }
    
    /**
     * Security monitoring: Check for suspicious authentication activity
     */
    public boolean hasSuspiciousAuthenticationActivity(String ipAddress, int minutes, int maxFailures) {
        Duration duration = Duration.ofMinutes(minutes);
        long failureCount = countAuthenticationFailuresByIp(ipAddress, duration);
        return failureCount >= maxFailures;
    }
    
    /**
     * Security monitoring: Check for user account brute force attempts
     */
    public boolean hasUserBruteForceAttempts(String username, int minutes, int maxFailures) {
        Duration duration = Duration.ofMinutes(minutes);
        long failureCount = countAuthenticationFailuresByUsername(username, duration);
        return failureCount >= maxFailures;
    }
    
    /**
     * Clean up old transaction logs (for log retention)
     */
    @Async
    public CompletableFuture<Void> cleanupOldLogs(int retentionDays) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            repository.deleteOldLogs(cutoff);
            logger.info("Cleaned up transaction logs older than {} days", retentionDays);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            logger.error("Failed to cleanup old transaction logs: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Helper method to create a basic transaction log
     */
    public TransactionLog createTransactionLog(String category, String component, String source, String message) {
        return new TransactionLog(category, component, source, message);
    }
    
    /**
     * Helper method to create an authentication transaction log
     */
    public TransactionLog createAuthenticationLog(String category, String message, String username, 
                                                 String ipAddress, String userAgent, String sessionId) {
        TransactionLog log = new TransactionLog(category, "authentication", "security", message);
        log.setUsername(username);
        log.setIpAddress(ipAddress);
        log.setUserAgent(userAgent);
        log.setSessionId(sessionId);
        return log;
    }
    
    /**
     * Helper method to create a file processing transaction log
     */
    public TransactionLog createFileProcessingLog(String category, String message, String fileName, 
                                                 String correlationId, UUID adapterId) {
        TransactionLog log = new TransactionLog(category, "file_adapter", "file_processing", message);
        log.setFileName(fileName);
        log.setCorrelationId(correlationId);
        log.setAdapterId(adapterId);
        return log;
    }
}