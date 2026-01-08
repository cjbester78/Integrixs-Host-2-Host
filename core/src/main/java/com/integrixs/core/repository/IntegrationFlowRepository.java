package com.integrixs.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrixs.shared.model.IntegrationFlow;
import com.integrixs.core.service.AuditService;
import com.integrixs.shared.util.AuditUtils;
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
 * JDBC Repository for integration flow management
 * Handles CRUD operations for visual flow definitions
 */
@Repository
public class IntegrationFlowRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    
    @Autowired
    public IntegrationFlowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }
    
    /**
     * Find all flows
     */
    public List<IntegrationFlow> findAll() {
        String sql = """
            SELECT id, name, description, bank_name, flow_definition, flow_version, flow_type,
                   max_parallel_executions, timeout_minutes, retry_policy,
                   total_executions, successful_executions, failed_executions, 
                   average_execution_time_ms, schedule_enabled, schedule_cron, 
                   next_scheduled_run, active, original_flow_id, created_at, updated_at, created_by, updated_by
            FROM integration_flows 
            ORDER BY created_at DESC
        """;
        
        return jdbcTemplate.query(sql, this::mapIntegrationFlow);
    }
    
    /**
     * Find flows by enabled status
     */
    public List<IntegrationFlow> findByActive(boolean active) {
        String sql = """
            SELECT id, name, description, bank_name, flow_definition, flow_version, flow_type,
                   max_parallel_executions, timeout_minutes, retry_policy,
                   total_executions, successful_executions, failed_executions, 
                   average_execution_time_ms, schedule_enabled, schedule_cron, 
                   next_scheduled_run, active, original_flow_id, created_at, updated_at, created_by, updated_by
            FROM integration_flows 
            WHERE active = ?
            ORDER BY name ASC
        """;
        
        return jdbcTemplate.query(sql, this::mapIntegrationFlow, active);
    }
    
    /**
     * Find scheduled flows
     */
    public List<IntegrationFlow> findScheduledFlows() {
        String sql = """
            SELECT id, name, description, bank_name, flow_definition, flow_version, flow_type,
                   max_parallel_executions, timeout_minutes, retry_policy,
                   total_executions, successful_executions, failed_executions, 
                   average_execution_time_ms, schedule_enabled, schedule_cron, 
                   next_scheduled_run, active, created_at, updated_at, created_by, updated_by
            FROM integration_flows 
            WHERE active = true AND schedule_enabled = true
            ORDER BY next_scheduled_run ASC
        """;
        
        return jdbcTemplate.query(sql, this::mapIntegrationFlow);
    }
    
    /**
     * Find flow by ID
     */
    public Optional<IntegrationFlow> findById(UUID id) {
        String sql = """
            SELECT id, name, description, bank_name, flow_definition, flow_version, flow_type,
                   max_parallel_executions, timeout_minutes, retry_policy,
                   total_executions, successful_executions, failed_executions, 
                   average_execution_time_ms, schedule_enabled, schedule_cron, 
                   next_scheduled_run, active, original_flow_id, created_at, updated_at, created_by, updated_by
            FROM integration_flows 
            WHERE id = ?
        """;
        
        try {
            IntegrationFlow flow = jdbcTemplate.queryForObject(sql, this::mapIntegrationFlow, id);
            return Optional.of(flow);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find flow by name
     */
    public Optional<IntegrationFlow> findByName(String name) {
        String sql = """
            SELECT id, name, description, bank_name, flow_definition, flow_version, flow_type,
                   max_parallel_executions, timeout_minutes, retry_policy,
                   total_executions, successful_executions, failed_executions, 
                   average_execution_time_ms, schedule_enabled, schedule_cron, 
                   next_scheduled_run, active, original_flow_id, created_at, updated_at, created_by, updated_by
            FROM integration_flows 
            WHERE name = ?
        """;
        
        try {
            IntegrationFlow flow = jdbcTemplate.queryForObject(sql, this::mapIntegrationFlow, name);
            return Optional.of(flow);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if flow exists by name
     */
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM integration_flows WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }
    
    /**
     * Check if flow has been imported by original flow ID
     */
    public boolean existsByOriginalFlowId(UUID originalFlowId) {
        String sql = "SELECT COUNT(*) FROM integration_flows WHERE original_flow_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, originalFlowId);
        return count != null && count > 0;
    }
    
    /**
     * Save flow (insert)
     */
    public UUID save(IntegrationFlow flow) {
        if (flow.getId() == null) {
            flow.setId(UUID.randomUUID());
        }
        
        // Set audit fields for INSERT
        flow.setCreatedAt(LocalDateTime.now());
        // Don't set updated_at for new records
        
        String sql = """
            INSERT INTO integration_flows (
                id, name, description, bank_name, flow_definition, flow_version, flow_type,
                max_parallel_executions, timeout_minutes, retry_policy,
                total_executions, successful_executions, failed_executions, 
                average_execution_time_ms, schedule_enabled, schedule_cron, 
                next_scheduled_run, active, original_flow_id, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        String createdByStr = AuditUtils.getCurrentUserId();
        UUID createdBy;
        try {
            createdBy = UUID.fromString(createdByStr);
        } catch (IllegalArgumentException e) {
            // If still not a valid UUID, use the createdBy from the flow object if available
            if (flow.getCreatedBy() != null) {
                createdBy = flow.getCreatedBy();
            } else {
                throw new RuntimeException("Unable to determine valid user ID for audit trail: " + createdByStr);
            }
        }
        
        jdbcTemplate.update(sql,
            flow.getId(),
            flow.getName(),
            flow.getDescription(),
            flow.getBankName(),
            convertMapToJson(flow.getFlowDefinition()),
            flow.getFlowVersion(),
            flow.getFlowType(),
            flow.getMaxParallelExecutions(),
            flow.getTimeoutMinutes(),
            convertMapToJson(flow.getRetryPolicy()),
            flow.getTotalExecutions(),
            flow.getSuccessfulExecutions(),
            flow.getFailedExecutions(),
            flow.getAverageExecutionTimeMs(),
            flow.getScheduleEnabled(),
            flow.getScheduleCron(),
            flow.getNextScheduledRun(),
            flow.getActive(),
            flow.getOriginalFlowId(),
            flow.getCreatedAt(),
            createdBy
        );
        
        // Log audit trail for flow creation
        auditService.logDatabaseOperation("INSERT", "integration_flows", flow.getId(), 
            flow.getName(), true, null);
        
        return flow.getId();
    }
    
    /**
     * Update flow
     */
    public void update(IntegrationFlow flow) {
        flow.setUpdatedAt(LocalDateTime.now());
        String updatedByStr = AuditUtils.getCurrentUserId();
        UUID updatedBy;
        try {
            updatedBy = UUID.fromString(updatedByStr);
        } catch (IllegalArgumentException e) {
            // If still not a valid UUID, use the updatedBy from the flow object if available
            if (flow.getUpdatedBy() != null) {
                updatedBy = flow.getUpdatedBy();
            } else {
                throw new RuntimeException("Unable to determine valid user ID for audit trail: " + updatedByStr);
            }
        }
        
        String sql = """
            UPDATE integration_flows SET
                name = ?, description = ?, bank_name = ?, flow_definition = ?::jsonb, flow_version = ?,
                flow_type = ?, max_parallel_executions = ?, timeout_minutes = ?,
                retry_policy = ?::jsonb, total_executions = ?, successful_executions = ?,
                failed_executions = ?, average_execution_time_ms = ?, schedule_enabled = ?,
                schedule_cron = ?, next_scheduled_run = ?, active = ?, updated_at = ?, updated_by = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql,
            flow.getName(),
            flow.getDescription(),
            flow.getBankName(),
            convertMapToJson(flow.getFlowDefinition()),
            flow.getFlowVersion(),
            flow.getFlowType(),
            flow.getMaxParallelExecutions(),
            flow.getTimeoutMinutes(),
            convertMapToJson(flow.getRetryPolicy()),
            flow.getTotalExecutions(),
            flow.getSuccessfulExecutions(),
            flow.getFailedExecutions(),
            flow.getAverageExecutionTimeMs(),
            flow.getScheduleEnabled(),
            flow.getScheduleCron(),
            flow.getNextScheduledRun(),
            flow.getActive(),
            flow.getUpdatedAt(),
            updatedBy,
            flow.getId()
        );
    }
    
    /**
     * Set flow enabled/disabled
     */
    public void setActive(UUID id, boolean active) {
        String sql = """
            UPDATE integration_flows SET
                active = ?,
                updated_at = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql, active, LocalDateTime.now(), id);
    }
    
    /**
     * Delete flow by ID
     */
    public boolean deleteById(UUID id) {
        // Get flow info before deletion for audit trail
        Optional<IntegrationFlow> flowOpt = findById(id);
        String flowName = flowOpt.map(IntegrationFlow::getName).orElse("unknown");
        
        String sql = "DELETE FROM integration_flows WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        // Log audit trail for flow deletion
        auditService.logDatabaseOperation("DELETE", "integration_flows", id, 
            flowName, rowsAffected > 0, rowsAffected == 0 ? "Flow not found" : null);
        
        return rowsAffected > 0;
    }
    
    /**
     * Get flow statistics
     */
    public Map<String, Object> getFlowStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_flows,
                COUNT(*) FILTER (WHERE active = true) as active_flows,
                COUNT(*) FILTER (WHERE schedule_enabled = true) as scheduled_flows,
                SUM(total_executions) as total_executions,
                SUM(successful_executions) as total_successes,
                SUM(failed_executions) as total_failures,
                AVG(average_execution_time_ms) as avg_execution_time
            FROM integration_flows
        """;
        
        return jdbcTemplate.queryForMap(sql);
    }
    
    /**
     * Update deployment status for a flow
     * NOTE: Deployment status is now tracked only in deployed_flows table
     * This method is kept for backward compatibility but does minimal work
     */
    public void updateDeploymentStatus(UUID flowId, String deploymentStatus, LocalDateTime timestamp, UUID userId) {
        // Deployment status is now tracked only in deployed_flows table
        // Log audit trail for deployment status change  
        auditService.logDatabaseOperation("UPDATE", "integration_flows", flowId, 
            "Deployment Status: " + deploymentStatus + " (tracked in deployed_flows)", true, null);
    }
    
    /**
     * Log deployment operation to flow_deployment_log
     */
    public void logDeployment(UUID flowId, String operation, String operationStatus, 
                             LocalDateTime timestamp, UUID deployedBy, 
                             Map<String, Object> flowDefinition, 
                             UUID senderAdapterId, UUID receiverAdapterId) {
        logDeployment(flowId, operation, operationStatus, timestamp, deployedBy, 
                     flowDefinition, senderAdapterId, receiverAdapterId, null);
    }
    
    /**
     * Log deployment operation to flow_deployment_log with error message
     */
    public void logDeployment(UUID flowId, String operation, String operationStatus, 
                             LocalDateTime timestamp, UUID deployedBy, 
                             Map<String, Object> flowDefinition, 
                             UUID senderAdapterId, UUID receiverAdapterId,
                             String errorMessage) {
        String sql = """
            INSERT INTO flow_deployment_log (
                flow_id, operation, operation_status, started_at, completed_at,
                deployment_config, error_message, validation_passed,
                sender_adapter_id, receiver_adapter_id, deployed_by
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
        """;
        
        boolean validationPassed = "SUCCESS".equals(operationStatus);
        
        jdbcTemplate.update(sql,
            flowId,
            operation,
            operationStatus,
            timestamp,
            timestamp, // Use same timestamp for completed_at for now
            convertMapToJson(flowDefinition),
            errorMessage,
            validationPassed,
            senderAdapterId,
            receiverAdapterId,
            deployedBy);
    }
    
    /**
     * Get deployment history for a flow
     */
    public List<Map<String, Object>> getDeploymentHistory(UUID flowId) {
        String sql = """
            SELECT id, operation, operation_status, started_at, completed_at,
                   error_message, validation_passed, deployed_by, correlation_id
            FROM flow_deployment_log
            WHERE flow_id = ?
            ORDER BY started_at DESC
            LIMIT 50
        """;
        
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> log = new java.util.HashMap<>();
            log.put("id", UUID.fromString(rs.getString("id")));
            log.put("operation", rs.getString("operation"));
            log.put("operationStatus", rs.getString("operation_status"));
            
            Timestamp startedAt = rs.getTimestamp("started_at");
            if (startedAt != null) {
                log.put("startedAt", startedAt.toLocalDateTime());
            }
            
            Timestamp completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) {
                log.put("completedAt", completedAt.toLocalDateTime());
            }
            
            log.put("errorMessage", rs.getString("error_message"));
            log.put("validationPassed", rs.getBoolean("validation_passed"));
            
            String deployedBy = rs.getString("deployed_by");
            if (deployedBy != null) {
                log.put("deployedBy", UUID.fromString(deployedBy));
            }
            
            String correlationId = rs.getString("correlation_id");
            if (correlationId != null) {
                log.put("correlationId", UUID.fromString(correlationId));
            }
            
            return log;
        }, flowId);
    }
    
    /**
     * Maps ResultSet row to IntegrationFlow entity
     */
    private IntegrationFlow mapIntegrationFlow(ResultSet rs, int rowNum) throws SQLException {
        IntegrationFlow flow = new IntegrationFlow();
        
        flow.setId(UUID.fromString(rs.getString("id")));
        flow.setName(rs.getString("name"));
        flow.setDescription(rs.getString("description"));
        flow.setBankName(rs.getString("bank_name"));
        flow.setFlowDefinition(convertJsonToMap(rs.getString("flow_definition")));
        flow.setFlowVersion(rs.getInt("flow_version"));
        flow.setFlowType(rs.getString("flow_type"));
        flow.setMaxParallelExecutions(rs.getInt("max_parallel_executions"));
        flow.setTimeoutMinutes(rs.getInt("timeout_minutes"));
        flow.setRetryPolicy(convertJsonToMap(rs.getString("retry_policy")));
        flow.setTotalExecutions(rs.getLong("total_executions"));
        flow.setSuccessfulExecutions(rs.getLong("successful_executions"));
        flow.setFailedExecutions(rs.getLong("failed_executions"));
        flow.setAverageExecutionTimeMs(rs.getLong("average_execution_time_ms"));
        flow.setScheduleEnabled(rs.getBoolean("schedule_enabled"));
        flow.setScheduleCron(rs.getString("schedule_cron"));
        
        Timestamp nextScheduledRun = rs.getTimestamp("next_scheduled_run");
        if (nextScheduledRun != null) {
            flow.setNextScheduledRun(nextScheduledRun.toLocalDateTime());
        }
        
        flow.setActive(rs.getBoolean("active"));
        
        String originalFlowId = rs.getString("original_flow_id");
        if (originalFlowId != null) {
            flow.setOriginalFlowId(UUID.fromString(originalFlowId));
        }
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            flow.setCreatedAt(createdAt.toLocalDateTime());
        }
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            flow.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        String createdBy = rs.getString("created_by");
        if (createdBy != null) {
            flow.setCreatedBy(UUID.fromString(createdBy));
        }
        
        String updatedBy = rs.getString("updated_by");
        if (updatedBy != null) {
            flow.setUpdatedBy(UUID.fromString(updatedBy));
        }
        
        return flow;
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
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert map to JSON", e);
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
        } catch (JsonProcessingException e) {
            // Return empty map for invalid JSON instead of throwing exception
            return new java.util.HashMap<>();
        }
    }
}