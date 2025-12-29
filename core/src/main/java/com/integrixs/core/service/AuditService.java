package com.integrixs.core.service;

import com.integrixs.shared.repository.SystemAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Simple audit service for core module database operations
 * 
 * This service provides basic audit logging functionality for repositories
 * in the core module without web context dependencies.
 */
@Service
public class AuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    private final SystemAuditLogRepository auditLogRepository;
    
    public AuditService(SystemAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    /**
     * Log a database operation with minimal context
     * 
     * This method provides basic audit logging for database operations
     * without requiring web context or security context.
     */
    public void logDatabaseOperation(String operation, String tableName, UUID entityId, 
                                   String entityName, boolean success, String errorMessage) {
        try {
            auditLogRepository.logDatabaseOperation(operation, tableName, entityId, 
                null, success, errorMessage);
                
            logger.info("AUDIT: {} {} on {} (ID: {}) - Success: {}", 
                       operation, entityName != null ? entityName : "entity", 
                       tableName, entityId, success);
                       
        } catch (Exception e) {
            logger.error("Failed to log database operation: {} on {} - Error: {}", 
                        operation, tableName, e.getMessage(), e);
        }
    }
    
    /**
     * Log a successful database operation
     */
    public void logSuccess(String operation, String tableName, UUID entityId, String entityName) {
        logDatabaseOperation(operation, tableName, entityId, entityName, true, null);
    }
    
    /**
     * Log a failed database operation
     */
    public void logFailure(String operation, String tableName, UUID entityId, String entityName, String errorMessage) {
        logDatabaseOperation(operation, tableName, entityId, entityName, false, errorMessage);
    }
}