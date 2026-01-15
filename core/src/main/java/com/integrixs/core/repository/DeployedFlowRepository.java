package com.integrixs.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrixs.shared.model.DeployedFlow;
import com.integrixs.core.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository for deployed flows execution registry
 * This repository manages the deployed_flows table which determines what can execute
 */
@Repository
public class DeployedFlowRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(DeployedFlowRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    
    @Autowired
    public DeployedFlowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }
    
    /**
     * Check if flow can execute using the database function
     */
    public Map<String, Object> checkFlowExecution(UUID flowId) {
        String sql = "SELECT * FROM can_flow_execute(?)";
        
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("canExecute", rs.getBoolean("can_execute"));
                
                String deploymentIdStr = rs.getString("deployment_id");
                if (deploymentIdStr != null) {
                    result.put("deploymentId", UUID.fromString(deploymentIdStr));
                }
                
                result.put("runtimeStatus", rs.getString("runtime_status"));
                result.put("adaptersReady", rs.getBoolean("adapters_ready"));
                result.put("reason", rs.getString("reason"));
                
                return result;
            }, flowId);
        } catch (EmptyResultDataAccessException e) {
            // No deployment found
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("canExecute", false);
            result.put("deploymentId", null);
            result.put("runtimeStatus", "NOT_DEPLOYED");
            result.put("adaptersReady", false);
            result.put("reason", "Flow is not deployed");
            return result;
        }
    }
    
    /**
     * Find all deployed flows
     */
    public List<DeployedFlow> findAll() {
        String sql = """
            SELECT id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                   sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                   execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                   retry_policy, total_executions, successful_executions, failed_executions,
                   last_execution_at, last_execution_status, last_execution_duration_ms, average_execution_time_ms,
                   last_error_at, last_error_message, consecutive_failures,
                   flow_configuration,
                   deployed_at, deployed_by, undeployed_at, undeployed_by, deployment_notes,
                   health_check_enabled, last_health_check_at, health_check_status, health_check_message
            FROM deployed_flows
            ORDER BY deployed_at DESC
        """;

        return jdbcTemplate.query(sql, this::mapDeployedFlow);
    }
    
    /**
     * Find deployed flows by status
     */
    public List<DeployedFlow> findByDeploymentStatus(DeployedFlow.DeploymentStatus status) {
        String sql = """
            SELECT id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                   sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                   execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                   retry_policy, total_executions, successful_executions, failed_executions,
                   last_execution_at, last_execution_status, last_execution_duration_ms, average_execution_time_ms,
                   last_error_at, last_error_message, consecutive_failures,
                   flow_configuration,
                   deployed_at, deployed_by, undeployed_at, undeployed_by, deployment_notes,
                   health_check_enabled, last_health_check_at, health_check_status, health_check_message
            FROM deployed_flows
            WHERE deployment_status = ?
            ORDER BY deployed_at DESC
        """;

        return jdbcTemplate.query(sql, this::mapDeployedFlow, status.name());
    }
    
    /**
     * Find executable flows (deployed and enabled)
     */
    public List<DeployedFlow> findExecutableFlows() {
        String sql = """
            SELECT id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                   sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                   execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                   retry_policy, total_executions, successful_executions, failed_executions,
                   last_execution_at, last_execution_status, last_execution_duration_ms, average_execution_time_ms,
                   last_error_at, last_error_message, consecutive_failures,
                   flow_configuration,
                   deployed_at, deployed_by, undeployed_at, undeployed_by, deployment_notes,
                   health_check_enabled, last_health_check_at, health_check_status, health_check_message
            FROM deployed_flows
            WHERE deployment_status = 'DEPLOYED'
            AND execution_enabled = true
            AND runtime_status = 'ACTIVE'
            ORDER BY deployed_at DESC
        """;

        return jdbcTemplate.query(sql, this::mapDeployedFlow);
    }
    
    /**
     * Find deployed flow by flow ID (only DEPLOYED status)
     */
    public Optional<DeployedFlow> findByFlowId(UUID flowId) {
        String sql = """
            SELECT id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                   sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                   execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                   retry_policy, total_executions, successful_executions, failed_executions,
                   last_execution_at, last_execution_status, last_execution_duration_ms, average_execution_time_ms,
                   last_error_at, last_error_message, consecutive_failures,
                   flow_configuration,
                   deployed_at, deployed_by, undeployed_at, undeployed_by, deployment_notes,
                   health_check_enabled, last_health_check_at, health_check_status, health_check_message
            FROM deployed_flows
            WHERE flow_id = ?
            AND deployment_status = 'DEPLOYED'
            ORDER BY deployed_at DESC
            LIMIT 1
        """;

        try {
            DeployedFlow deployedFlow = jdbcTemplate.queryForObject(sql, this::mapDeployedFlow, flowId);
            return Optional.of(deployedFlow);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("No deployed flow found for flow ID: {}", flowId);
            return Optional.empty();
        }
    }

    /**
     * Find any deployment record by flow ID (regardless of status)
     * Used to check if re-deployment should update existing record
     */
    public Optional<DeployedFlow> findAnyByFlowId(UUID flowId) {
        String sql = """
            SELECT id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                   sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                   execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                   retry_policy, total_executions, successful_executions, failed_executions,
                   last_execution_at, last_execution_status, last_execution_duration_ms, average_execution_time_ms,
                   last_error_at, last_error_message, consecutive_failures,
                   flow_configuration,
                   deployed_at, deployed_by, undeployed_at, undeployed_by, deployment_notes,
                   health_check_enabled, last_health_check_at, health_check_status, health_check_message
            FROM deployed_flows
            WHERE flow_id = ?
            ORDER BY deployed_at DESC
            LIMIT 1
        """;

        try {
            DeployedFlow deployedFlow = jdbcTemplate.queryForObject(sql, this::mapDeployedFlow, flowId);
            return Optional.of(deployedFlow);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find ALL deployed flow instances for a given flow ID
     * Used for flows with multiple receiver adapters
     */
    public List<DeployedFlow> findAllByFlowId(UUID flowId) {
        String sql = """
            SELECT id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                   sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                   execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                   retry_policy, total_executions, successful_executions, failed_executions,
                   last_execution_at, last_execution_status, last_execution_duration_ms, average_execution_time_ms,
                   last_error_at, last_error_message, consecutive_failures,
                   flow_configuration,
                   deployed_at, deployed_by, undeployed_at, undeployed_by, deployment_notes,
                   health_check_enabled, last_health_check_at, health_check_status, health_check_message
            FROM deployed_flows
            WHERE flow_id = ?
            AND deployment_status = 'DEPLOYED'
            ORDER BY deployed_at DESC
        """;

        try {
            List<DeployedFlow> deployedFlows = jdbcTemplate.query(sql, this::mapDeployedFlow, flowId);
            logger.debug("Found {} deployed flow instances for flow ID: {}", deployedFlows.size(), flowId);
            return deployedFlows;
        } catch (Exception e) {
            logger.error("Error finding deployed flows for flow ID {}: {}", flowId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Find deployed flow by ID
     */
    public Optional<DeployedFlow> findById(UUID id) {
        String sql = """
            SELECT id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                   sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                   execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                   retry_policy, total_executions, successful_executions, failed_executions,
                   last_execution_at, last_execution_status, last_execution_duration_ms, average_execution_time_ms,
                   last_error_at, last_error_message, consecutive_failures,
                   flow_configuration,
                   deployed_at, deployed_by, undeployed_at, undeployed_by, deployment_notes,
                   health_check_enabled, last_health_check_at, health_check_status, health_check_message
            FROM deployed_flows
            WHERE id = ?
        """;

        try {
            DeployedFlow deployedFlow = jdbcTemplate.queryForObject(sql, this::mapDeployedFlow, id);
            return Optional.of(deployedFlow);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Deploy a flow to the execution registry
     */
    public UUID deploy(DeployedFlow deployedFlow) {
        if (deployedFlow.getId() == null) {
            deployedFlow.setId(UUID.randomUUID());
        }

        String sql = """
            INSERT INTO deployed_flows (
                id, flow_id, flow_name, flow_version, deployment_status, deployment_environment,
                sender_adapter_id, sender_adapter_name, receiver_adapter_id, receiver_adapter_name,
                execution_enabled, runtime_status, max_concurrent_executions, execution_timeout_minutes,
                retry_policy, flow_configuration,
                deployed_at, deployed_by, deployment_notes, health_check_enabled
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
        """;

        jdbcTemplate.update(sql,
            deployedFlow.getId(),
            deployedFlow.getFlowId(),
            deployedFlow.getFlowName(),
            deployedFlow.getFlowVersion(),
            deployedFlow.getDeploymentStatus().name(),
            deployedFlow.getDeploymentEnvironment(),
            deployedFlow.getSenderAdapterId(),
            deployedFlow.getSenderAdapterName(),
            deployedFlow.getReceiverAdapterId(),
            deployedFlow.getReceiverAdapterName(),
            deployedFlow.getExecutionEnabled(),
            deployedFlow.getRuntimeStatus().name(),
            deployedFlow.getMaxConcurrentExecutions(),
            deployedFlow.getExecutionTimeoutMinutes(),
            convertMapToJson(deployedFlow.getRetryPolicy()),
            convertMapToJson(deployedFlow.getFlowConfiguration()),
            deployedFlow.getDeployedAt(),
            deployedFlow.getDeployedBy(),
            deployedFlow.getDeploymentNotes(),
            deployedFlow.getHealthCheckEnabled()
        );

        // Log audit trail
        auditService.logDatabaseOperation("INSERT", "deployed_flows", deployedFlow.getId(),
            deployedFlow.getDisplayName(), true, null);

        return deployedFlow.getId();
    }

    /**
     * Re-deploy a previously undeployed flow (update existing record)
     */
    public void redeploy(DeployedFlow deployedFlow) {
        String sql = """
            UPDATE deployed_flows SET
                flow_name = ?, flow_version = ?,
                deployment_status = ?, runtime_status = ?,
                sender_adapter_id = ?, sender_adapter_name = ?,
                receiver_adapter_id = ?, receiver_adapter_name = ?,
                execution_enabled = ?, max_concurrent_executions = ?, execution_timeout_minutes = ?,
                flow_configuration = ?::jsonb,
                deployed_at = ?, deployed_by = ?,
                undeployed_at = NULL, undeployed_by = NULL,
                health_check_enabled = ?
            WHERE id = ?
        """;

        jdbcTemplate.update(sql,
            deployedFlow.getFlowName(),
            deployedFlow.getFlowVersion(),
            deployedFlow.getDeploymentStatus().name(),
            deployedFlow.getRuntimeStatus().name(),
            deployedFlow.getSenderAdapterId(),
            deployedFlow.getSenderAdapterName(),
            deployedFlow.getReceiverAdapterId(),
            deployedFlow.getReceiverAdapterName(),
            deployedFlow.getExecutionEnabled(),
            deployedFlow.getMaxConcurrentExecutions(),
            deployedFlow.getExecutionTimeoutMinutes(),
            convertMapToJson(deployedFlow.getFlowConfiguration()),
            deployedFlow.getDeployedAt(),
            deployedFlow.getDeployedBy(),
            deployedFlow.getHealthCheckEnabled(),
            deployedFlow.getId()
        );

        // Log audit trail
        auditService.logDatabaseOperation("UPDATE", "deployed_flows", deployedFlow.getId(),
            "Re-deployed: " + deployedFlow.getDisplayName(), true, null);
    }

    /**
     * Update deployed flow
     */
    public void update(DeployedFlow deployedFlow) {
        // Note: No longer tracking generic updated_at - using flow-specific timestamps
        
        String sql = """
            UPDATE deployed_flows SET
                deployment_status = ?, runtime_status = ?, execution_enabled = ?,
                max_concurrent_executions = ?, execution_timeout_minutes = ?,
                retry_policy = ?::jsonb, deployment_notes = ?,
                last_execution_at = ?, last_execution_status = ?, last_execution_duration_ms = ?,
                average_execution_time_ms = ?, total_executions = ?, successful_executions = ?,
                failed_executions = ?, last_error_at = ?, last_error_message = ?,
                consecutive_failures = ?, health_check_status = ?, health_check_message = ?,
                last_health_check_at = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql,
            deployedFlow.getDeploymentStatus().name(),
            deployedFlow.getRuntimeStatus().name(),
            deployedFlow.getExecutionEnabled(),
            deployedFlow.getMaxConcurrentExecutions(),
            deployedFlow.getExecutionTimeoutMinutes(),
            convertMapToJson(deployedFlow.getRetryPolicy()),
            deployedFlow.getDeploymentNotes(),
            deployedFlow.getLastExecutionAt(),
            deployedFlow.getLastExecutionStatus(),
            deployedFlow.getLastExecutionDurationMs(),
            deployedFlow.getAverageExecutionTimeMs(),
            deployedFlow.getTotalExecutions(),
            deployedFlow.getSuccessfulExecutions(),
            deployedFlow.getFailedExecutions(),
            deployedFlow.getLastErrorAt(),
            deployedFlow.getLastErrorMessage(),
            deployedFlow.getConsecutiveFailures(),
            deployedFlow.getHealthCheckStatus() != null ? deployedFlow.getHealthCheckStatus().name() : null,
            deployedFlow.getHealthCheckMessage(),
            deployedFlow.getLastHealthCheckAt(),
            deployedFlow.getId()
        );
    }
    
    /**
     * Undeploy a flow (remove from execution registry completely)
     */
    public void undeploy(UUID deployedFlowId, UUID undeployedBy) {
        // Get flow info for audit logging before deletion
        Optional<DeployedFlow> deployedFlowOpt = findById(deployedFlowId);
        String displayName = deployedFlowOpt.map(DeployedFlow::getDisplayName).orElse("unknown");
        
        String sql = "DELETE FROM deployed_flows WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, deployedFlowId);
        
        // Log audit trail
        auditService.logDatabaseOperation("DELETE", "deployed_flows", deployedFlowId, 
            "Undeployed and removed from registry", rowsAffected > 0, 
            rowsAffected == 0 ? "Deployed flow not found" : null);
    }
    
    /**
     * Remove deployed flow completely
     */
    public boolean deleteById(UUID id) {
        Optional<DeployedFlow> deployedFlowOpt = findById(id);
        String displayName = deployedFlowOpt.map(DeployedFlow::getDisplayName).orElse("unknown");
        
        String sql = "DELETE FROM deployed_flows WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        // Log audit trail
        auditService.logDatabaseOperation("DELETE", "deployed_flows", id, 
            displayName, rowsAffected > 0, rowsAffected == 0 ? "Deployed flow not found" : null);
        
        return rowsAffected > 0;
    }
    
    /**
     * Get deployment statistics
     */
    public Map<String, Object> getDeploymentStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_deployments,
                COUNT(*) FILTER (WHERE deployment_status = 'DEPLOYED') as deployed_count,
                COUNT(*) FILTER (WHERE execution_enabled = true) as enabled_count,
                COUNT(*) FILTER (WHERE runtime_status = 'ACTIVE') as active_count,
                SUM(total_executions) as total_executions,
                SUM(successful_executions) as total_successes,
                SUM(failed_executions) as total_failures,
                AVG(average_execution_time_ms) as avg_execution_time
            FROM deployed_flows
        """;
        
        return jdbcTemplate.queryForMap(sql);
    }
    
    /**
     * Map ResultSet to DeployedFlow entity
     */
    private DeployedFlow mapDeployedFlow(ResultSet rs, int rowNum) throws SQLException {
        DeployedFlow deployedFlow = new DeployedFlow();
        
        deployedFlow.setId(UUID.fromString(rs.getString("id")));
        deployedFlow.setFlowId(UUID.fromString(rs.getString("flow_id")));
        deployedFlow.setFlowName(rs.getString("flow_name"));
        deployedFlow.setFlowVersion(rs.getInt("flow_version"));
        deployedFlow.setDeploymentStatus(DeployedFlow.DeploymentStatus.valueOf(rs.getString("deployment_status")));
        deployedFlow.setDeploymentEnvironment(rs.getString("deployment_environment"));
        
        String senderAdapterId = rs.getString("sender_adapter_id");
        if (senderAdapterId != null) {
            deployedFlow.setSenderAdapterId(UUID.fromString(senderAdapterId));
        }
        deployedFlow.setSenderAdapterName(rs.getString("sender_adapter_name"));
        
        String receiverAdapterId = rs.getString("receiver_adapter_id");
        if (receiverAdapterId != null) {
            deployedFlow.setReceiverAdapterId(UUID.fromString(receiverAdapterId));
        }
        deployedFlow.setReceiverAdapterName(rs.getString("receiver_adapter_name"));
        
        deployedFlow.setExecutionEnabled(rs.getBoolean("execution_enabled"));
        deployedFlow.setRuntimeStatus(DeployedFlow.RuntimeStatus.valueOf(rs.getString("runtime_status")));
        deployedFlow.setMaxConcurrentExecutions(rs.getInt("max_concurrent_executions"));
        deployedFlow.setExecutionTimeoutMinutes(rs.getInt("execution_timeout_minutes"));
        deployedFlow.setRetryPolicy(convertJsonToMap(rs.getString("retry_policy")));
        
        deployedFlow.setTotalExecutions(rs.getLong("total_executions"));
        deployedFlow.setSuccessfulExecutions(rs.getLong("successful_executions"));
        deployedFlow.setFailedExecutions(rs.getLong("failed_executions"));
        
        Timestamp lastExecutionAt = rs.getTimestamp("last_execution_at");
        if (lastExecutionAt != null) {
            deployedFlow.setLastExecutionAt(lastExecutionAt.toLocalDateTime());
        }
        deployedFlow.setLastExecutionStatus(rs.getString("last_execution_status"));
        deployedFlow.setLastExecutionDurationMs(rs.getLong("last_execution_duration_ms"));
        deployedFlow.setAverageExecutionTimeMs(rs.getLong("average_execution_time_ms"));
        
        Timestamp lastErrorAt = rs.getTimestamp("last_error_at");
        if (lastErrorAt != null) {
            deployedFlow.setLastErrorAt(lastErrorAt.toLocalDateTime());
        }
        deployedFlow.setLastErrorMessage(rs.getString("last_error_message"));
        deployedFlow.setConsecutiveFailures(rs.getInt("consecutive_failures"));
        
        deployedFlow.setFlowConfiguration(convertJsonToMap(rs.getString("flow_configuration")));
        
        Timestamp deployedAt = rs.getTimestamp("deployed_at");
        if (deployedAt != null) {
            deployedFlow.setDeployedAt(deployedAt.toLocalDateTime());
        }
        
        String deployedBy = rs.getString("deployed_by");
        if (deployedBy != null) {
            deployedFlow.setDeployedBy(UUID.fromString(deployedBy));
        }
        
        Timestamp undeployedAt = rs.getTimestamp("undeployed_at");
        if (undeployedAt != null) {
            deployedFlow.setUndeployedAt(undeployedAt.toLocalDateTime());
        }
        
        String undeployedBy = rs.getString("undeployed_by");
        if (undeployedBy != null) {
            deployedFlow.setUndeployedBy(UUID.fromString(undeployedBy));
        }
        
        deployedFlow.setDeploymentNotes(rs.getString("deployment_notes"));
        deployedFlow.setHealthCheckEnabled(rs.getBoolean("health_check_enabled"));
        
        Timestamp lastHealthCheckAt = rs.getTimestamp("last_health_check_at");
        if (lastHealthCheckAt != null) {
            deployedFlow.setLastHealthCheckAt(lastHealthCheckAt.toLocalDateTime());
        }
        
        String healthStatus = rs.getString("health_check_status");
        if (healthStatus != null) {
            deployedFlow.setHealthCheckStatus(DeployedFlow.HealthStatus.valueOf(healthStatus));
        }
        deployedFlow.setHealthCheckMessage(rs.getString("health_check_message"));
        
        // Note: Removed redundant audit field mapping
        // Using flow-specific deployment tracking fields instead
        
        return deployedFlow;
    }
    
    /**
     * Convert Map to JSON string
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
     * Convert JSON string to Map
     */
    private Map<String, Object> convertJsonToMap(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new java.util.HashMap<>();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return new java.util.HashMap<>();
        }
    }
}