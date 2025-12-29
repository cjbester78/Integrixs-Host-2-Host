package com.integrixs.core.repository;

import com.integrixs.shared.model.FlowExecution;
import com.integrixs.core.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC Repository for flow execution management
 * Handles CRUD operations for flow execution instances
 */
@Repository
public class FlowExecutionRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public FlowExecutionRepository(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Find all executions
     */
    public List<FlowExecution> findAll() {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            ORDER BY started_at DESC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionRowMapper());
    }
    
    /**
     * Find executions by flow ID
     */
    public List<FlowExecution> findByFlowId(UUID flowId) {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            WHERE flow_id = ?
            ORDER BY started_at DESC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionRowMapper(), flowId);
    }
    
    /**
     * Find executions by status
     */
    public List<FlowExecution> findByStatus(FlowExecution.ExecutionStatus status) {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            WHERE execution_status = ?
            ORDER BY started_at DESC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionRowMapper(), status.name());
    }
    
    /**
     * Find running executions
     */
    public List<FlowExecution> findRunningExecutions() {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            WHERE execution_status IN ('PENDING', 'RUNNING')
            ORDER BY started_at ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionRowMapper());
    }
    
    /**
     * Find recent executions with limit
     */
    public List<FlowExecution> findRecentExecutions(int limit) {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            ORDER BY started_at DESC
            LIMIT ?
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionRowMapper(), limit);
    }
    
    /**
     * Find failed executions eligible for retry
     */
    public List<FlowExecution> findFailedExecutionsForRetry() {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            WHERE execution_status = 'FAILED' 
              AND retry_attempt < max_retry_attempts
            ORDER BY started_at ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionRowMapper());
    }
    
    /**
     * Find execution by ID
     */
    public Optional<FlowExecution> findById(UUID id) {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            WHERE id = ?
        """;
        
        try {
            FlowExecution execution = jdbcTemplate.queryForObject(sql, new FlowExecutionRowMapper(), id);
            return Optional.of(execution);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find execution by correlation ID
     */
    public Optional<FlowExecution> findByCorrelationId(UUID correlationId) {
        String sql = """
            SELECT id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                   started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                   total_files_processed, files_successful, files_failed, total_bytes_processed,
                   error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                   correlation_id, parent_execution_id, priority, scheduled_for
            FROM flow_executions 
            WHERE correlation_id = ?
        """;
        
        try {
            FlowExecution execution = jdbcTemplate.queryForObject(sql, new FlowExecutionRowMapper(), correlationId);
            return Optional.of(execution);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Save execution (insert)
     */
    public UUID save(FlowExecution execution) {
        if (execution.getId() == null) {
            execution.setId(UUID.randomUUID());
        }
        
        String sql = """
            INSERT INTO flow_executions (
                id, flow_id, flow_name, execution_status, trigger_type, triggered_by,
                started_at, completed_at, timeout_at, duration_ms, payload, execution_context,
                total_files_processed, files_successful, files_failed, total_bytes_processed,
                error_message, error_details, error_step_id, retry_attempt, max_retry_attempts,
                correlation_id, parent_execution_id, priority, scheduled_for
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            execution.getId(),
            execution.getFlowId(),
            execution.getFlowName(),
            execution.getExecutionStatus().name(),
            execution.getTriggerType().name(),
            execution.getTriggeredBy(),
            execution.getStartedAt(),
            execution.getCompletedAt(),
            execution.getTimeoutAt(),
            execution.getDurationMs(),
            convertMapToJson(execution.getPayload()),
            convertMapToJson(execution.getExecutionContext()),
            execution.getTotalFilesProcessed(),
            execution.getFilesSuccessful(),
            execution.getFilesFailed(),
            execution.getTotalBytesProcessed(),
            execution.getErrorMessage(),
            convertMapToJson(execution.getErrorDetails()),
            execution.getErrorStepId(),
            execution.getRetryAttempt(),
            execution.getMaxRetryAttempts(),
            execution.getCorrelationId(),
            execution.getParentExecutionId(),
            execution.getPriority(),
            execution.getScheduledFor()
        );
        
        // Log audit trail for flow execution creation
        auditService.logDatabaseOperation("INSERT", "flow_executions", execution.getId(), 
            execution.getFlowName() + " execution", true, null);
        
        return execution.getId();
    }
    
    /**
     * Update execution
     */
    public void update(FlowExecution execution) {
        String sql = """
            UPDATE flow_executions SET
                execution_status = ?, completed_at = ?, duration_ms = ?,
                payload = ?::jsonb, execution_context = ?::jsonb, 
                total_files_processed = ?, files_successful = ?, files_failed = ?, 
                total_bytes_processed = ?, error_message = ?, error_details = ?::jsonb, 
                error_step_id = ?, retry_attempt = ?, timeout_at = ?, priority = ?,
                scheduled_for = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql,
            execution.getExecutionStatus().name(),
            execution.getCompletedAt(),
            execution.getDurationMs(),
            convertMapToJson(execution.getPayload()),
            convertMapToJson(execution.getExecutionContext()),
            execution.getTotalFilesProcessed(),
            execution.getFilesSuccessful(),
            execution.getFilesFailed(),
            execution.getTotalBytesProcessed(),
            execution.getErrorMessage(),
            convertMapToJson(execution.getErrorDetails()),
            execution.getErrorStepId(),
            execution.getRetryAttempt(),
            execution.getTimeoutAt(),
            execution.getPriority(),
            execution.getScheduledFor(),
            execution.getId()
        );
        
        // Log audit trail for flow execution update
        auditService.logDatabaseOperation("UPDATE", "flow_executions", execution.getId(), 
            execution.getFlowName() + " execution", true, null);
    }
    
    /**
     * Update execution status
     */
    public void updateStatus(UUID id, FlowExecution.ExecutionStatus status) {
        String sql = "UPDATE flow_executions SET execution_status = ? WHERE id = ?";
        jdbcTemplate.update(sql, status.name(), id);
        
        // Log audit trail for execution status update
        auditService.logDatabaseOperation("UPDATE", "flow_executions", id, 
            "execution status changed to " + status.name(), true, null);
    }
    
    /**
     * Update execution completion
     */
    public void updateCompletion(UUID id, FlowExecution.ExecutionStatus status, 
                               LocalDateTime completedAt, long durationMs, String errorMessage) {
        String sql = """
            UPDATE flow_executions SET
                execution_status = ?,
                completed_at = ?,
                duration_ms = ?,
                error_message = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql, status.name(), completedAt, durationMs, errorMessage, id);
        
        // Log audit trail for execution completion
        auditService.logDatabaseOperation("UPDATE", "flow_executions", id, 
            "execution completed with status " + status.name(), true, null);
    }
    
    /**
     * Update retry information
     */
    public void updateRetryInfo(UUID id, int retryAttempt) {
        String sql = "UPDATE flow_executions SET retry_attempt = ? WHERE id = ?";
        jdbcTemplate.update(sql, retryAttempt, id);
        
        // Log audit trail for retry attempt update
        auditService.logDatabaseOperation("UPDATE", "flow_executions", id, 
            "retry attempt " + retryAttempt, true, null);
    }
    
    /**
     * Delete execution by ID
     */
    public boolean deleteById(UUID id) {
        // Get execution info before deletion for audit trail
        Optional<FlowExecution> executionOpt = findById(id);
        String executionName = executionOpt.map(exec -> exec.getFlowName() + " execution").orElse("unknown execution");
        
        String sql = "DELETE FROM flow_executions WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        // Log audit trail for execution deletion
        auditService.logDatabaseOperation("DELETE", "flow_executions", id, 
            executionName, rowsAffected > 0, rowsAffected == 0 ? "Execution not found" : null);
        
        return rowsAffected > 0;
    }
    
    /**
     * Delete old executions beyond retention period
     */
    public int deleteOldExecutions(LocalDateTime beforeDate) {
        String sql = """
            DELETE FROM flow_executions 
            WHERE started_at < ? 
              AND execution_status IN ('COMPLETED', 'FAILED', 'CANCELLED')
        """;
        
        int deletedRows = jdbcTemplate.update(sql, beforeDate);
        
        // Log audit trail for bulk deletion of old executions
        if (deletedRows > 0) {
            auditService.logDatabaseOperation("DELETE", "flow_executions", null, 
                deletedRows + " old executions", true, null);
        }
        
        return deletedRows;
    }
    
    /**
     * Get execution statistics
     */
    public Map<String, Object> getExecutionStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_executions,
                COUNT(*) FILTER (WHERE execution_status = 'COMPLETED') as completed_executions,
                COUNT(*) FILTER (WHERE execution_status = 'FAILED') as failed_executions,
                COUNT(*) FILTER (WHERE execution_status = 'RUNNING') as running_executions,
                COUNT(*) FILTER (WHERE execution_status = 'PENDING') as pending_executions,
                AVG(duration_ms) as avg_duration_ms,
                SUM(total_files_processed) as total_files_processed,
                SUM(total_bytes_processed) as total_bytes_processed
            FROM flow_executions
            WHERE started_at >= CURRENT_DATE - INTERVAL '30 days'
        """;
        
        return jdbcTemplate.queryForMap(sql);
    }
    
    /**
     * Row mapper for FlowExecution entities
     */
    private static class FlowExecutionRowMapper implements RowMapper<FlowExecution> {
        @Override
        public FlowExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
            FlowExecution execution = new FlowExecution();
            
            execution.setId(UUID.fromString(rs.getString("id")));
            execution.setFlowId(UUID.fromString(rs.getString("flow_id")));
            execution.setFlowName(rs.getString("flow_name"));
            execution.setExecutionStatus(FlowExecution.ExecutionStatus.valueOf(rs.getString("execution_status")));
            execution.setTriggerType(FlowExecution.TriggerType.valueOf(rs.getString("trigger_type")));
            
            String triggeredBy = rs.getString("triggered_by");
            if (triggeredBy != null) {
                execution.setTriggeredBy(UUID.fromString(triggeredBy));
            }
            
            Timestamp startedAt = rs.getTimestamp("started_at");
            if (startedAt != null) {
                execution.setStartedAt(startedAt.toLocalDateTime());
            }
            
            Timestamp completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) {
                execution.setCompletedAt(completedAt.toLocalDateTime());
            }
            
            Timestamp timeoutAt = rs.getTimestamp("timeout_at");
            if (timeoutAt != null) {
                execution.setTimeoutAt(timeoutAt.toLocalDateTime());
            }
            
            execution.setDurationMs(rs.getLong("duration_ms"));
            execution.setPayload(convertJsonToMapStatic(rs.getString("payload")));
            execution.setExecutionContext(convertJsonToMapStatic(rs.getString("execution_context")));
            execution.setTotalFilesProcessed(rs.getInt("total_files_processed"));
            execution.setFilesSuccessful(rs.getInt("files_successful"));
            execution.setFilesFailed(rs.getInt("files_failed"));
            execution.setTotalBytesProcessed(rs.getLong("total_bytes_processed"));
            execution.setErrorMessage(rs.getString("error_message"));
            execution.setErrorDetails(convertJsonToMapStatic(rs.getString("error_details")));
            execution.setErrorStepId(rs.getString("error_step_id"));
            execution.setRetryAttempt(rs.getInt("retry_attempt"));
            execution.setMaxRetryAttempts(rs.getInt("max_retry_attempts"));
            
            String correlationId = rs.getString("correlation_id");
            if (correlationId != null) {
                execution.setCorrelationId(UUID.fromString(correlationId));
            }
            
            String parentExecutionId = rs.getString("parent_execution_id");
            if (parentExecutionId != null) {
                execution.setParentExecutionId(UUID.fromString(parentExecutionId));
            }
            
            execution.setPriority(rs.getInt("priority"));
            
            Timestamp scheduledFor = rs.getTimestamp("scheduled_for");
            if (scheduledFor != null) {
                execution.setScheduledFor(scheduledFor.toLocalDateTime());
            }
            
            return execution;
        }
    }
    
    /**
     * Convert Map to JSON string for database storage
     */
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convert JSON string to Map for entity mapping
     */
    private Map<String, Object> convertJsonToMap(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new java.util.HashMap<>();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }
    
    /**
     * Static version of JSON conversion for RowMapper
     */
    private static Map<String, Object> convertJsonToMapStatic(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new java.util.HashMap<>();
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }
}