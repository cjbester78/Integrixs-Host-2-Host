package com.integrixs.shared.repository;

import com.integrixs.shared.model.SystemAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for System Audit Log operations
 * 
 * This repository handles all database operations for the system_audit_log table.
 * Every database INSERT, UPDATE, and DELETE operation should be logged through this repository.
 */
@Repository
public class SystemAuditLogRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemAuditLogRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    public SystemAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Insert an audit log entry
     * 
     * This method should be called after every database operation (INSERT/UPDATE/DELETE)
     * to maintain a complete audit trail.
     */
    public void insertAuditLog(SystemAuditLog auditLog) {
        try {
            String sql = """
                INSERT INTO system_audit_log (
                    id, event_type, event_description, event_category, user_id, username,
                    user_ip, user_agent, resource_type, resource_id, resource_name,
                    additional_data, success, error_message, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            // Convert event details to JSON string
            String eventDetailsJson = auditLog.getEventDetails() != null 
                ? convertToJson(auditLog.getEventDetails()) 
                : null;
            
            int rowsAffected = jdbcTemplate.update(sql,
                auditLog.getId(),
                auditLog.getEventType(),
                auditLog.getEventDescription(),
                auditLog.getEventCategory(),
                auditLog.getUserId(),
                auditLog.getUsername(),
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getResourceName(),
                eventDetailsJson, // maps to additional_data
                auditLog.isSuccess(),
                auditLog.getErrorMessage(),
                auditLog.getCreatedAt()
            );
            
            if (rowsAffected == 1) {
                logger.debug("Successfully inserted audit log entry: {}", auditLog.getId());
            } else {
                logger.warn("Expected 1 row to be affected, but {} rows were affected for audit log: {}", 
                           rowsAffected, auditLog.getId());
            }
            
        } catch (DataAccessException e) {
            // Log the error but don't throw exception to avoid disrupting the main operation
            logger.error("Failed to insert audit log entry: {} - Error: {}", auditLog, e.getMessage(), e);
        }
    }
    
    /**
     * Insert a simple audit log entry for database operations
     * 
     * This is a convenience method for logging database operations with minimal information.
     */
    public void logDatabaseOperation(String operation, String tableName, UUID entityId, 
                                   UUID userId, boolean success, String errorMessage) {
        try {
            SystemAuditLog auditLog = new SystemAuditLog();
            auditLog.setEventType("DATABASE_" + operation.toUpperCase());
            auditLog.setEventCategory("SYSTEM");
            auditLog.setUserId(userId);
            auditLog.setEntityType(tableName.toUpperCase());
            auditLog.setEntityId(entityId);
            auditLog.setEventDescription(String.format("%s operation on %s table", operation, tableName));
            auditLog.setSuccess(success);
            auditLog.setErrorMessage(errorMessage);
            
            insertAuditLog(auditLog);
            
        } catch (Exception e) {
            logger.error("Failed to log database operation: {} on {} - Error: {}", 
                        operation, tableName, e.getMessage(), e);
        }
    }
    
    /**
     * Convert a map to JSON string
     * 
     * For now, this is a simple implementation. In production, you would use Jackson or Gson.
     */
    private String convertToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        
        try {
            // Simple JSON conversion - in production, use Jackson ObjectMapper
            if (obj instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
                StringBuilder json = new StringBuilder("{");
                boolean first = true;
                for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) json.append(",");
                    json.append("\"").append(entry.getKey()).append("\":\"")
                        .append(entry.getValue()).append("\"");
                    first = false;
                }
                json.append("}");
                return json.toString();
            }
            return obj.toString();
        } catch (Exception e) {
            logger.warn("Failed to convert object to JSON: {}", e.getMessage());
            return obj.toString();
        }
    }
}