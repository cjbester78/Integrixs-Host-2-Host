package com.integrixs.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrixs.shared.model.FlowExecutionStep;
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
 * JDBC Repository for flow execution step management
 * Handles CRUD operations for detailed step tracking
 */
@Repository
public class FlowExecutionStepRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public FlowExecutionStepRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Find all steps
     */
    public List<FlowExecutionStep> findAll() {
        String sql = """
            SELECT id, execution_id, step_id, step_name, step_type, step_order,
                   step_configuration, step_status, started_at, completed_at, duration_ms,
                   input_data, output_data, input_files, output_files, files_count,
                   bytes_processed, error_message, correlation_id
            FROM flow_execution_steps 
            ORDER BY step_order ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionStepRowMapper());
    }
    
    /**
     * Find steps by execution ID
     */
    public List<FlowExecutionStep> findByExecutionId(UUID executionId) {
        String sql = """
            SELECT id, execution_id, step_id, step_name, step_type, step_order,
                   step_configuration, step_status, started_at, completed_at, duration_ms,
                   input_data, output_data, input_files, output_files, files_count,
                   bytes_processed, error_message, correlation_id
            FROM flow_execution_steps 
            WHERE execution_id = ?
            ORDER BY step_order ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionStepRowMapper(), executionId);
    }
    
    /**
     * Find steps by execution ID and status
     */
    public List<FlowExecutionStep> findByExecutionIdAndStatus(UUID executionId, 
                                                             FlowExecutionStep.StepStatus status) {
        String sql = """
            SELECT id, execution_id, step_id, step_name, step_type, step_order,
                   step_configuration, step_status, started_at, completed_at, duration_ms,
                   input_data, output_data, input_files, output_files, files_count,
                   bytes_processed, error_message, correlation_id
            FROM flow_execution_steps 
            WHERE execution_id = ? AND step_status = ?
            ORDER BY step_order ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowExecutionStepRowMapper(), executionId, status.name());
    }
    
    /**
     * Find step by ID
     */
    public Optional<FlowExecutionStep> findById(UUID id) {
        String sql = """
            SELECT id, execution_id, step_id, step_name, step_type, step_order,
                   step_configuration, step_status, started_at, completed_at, duration_ms,
                   input_data, output_data, input_files, output_files, files_count,
                   bytes_processed, error_message, correlation_id
            FROM flow_execution_steps 
            WHERE id = ?
        """;
        
        try {
            FlowExecutionStep step = jdbcTemplate.queryForObject(sql, new FlowExecutionStepRowMapper(), id);
            return Optional.of(step);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Save step (insert)
     */
    public UUID save(FlowExecutionStep step) {
        if (step.getId() == null) {
            step.setId(UUID.randomUUID());
        }
        
        String sql = """
            INSERT INTO flow_execution_steps (
                id, execution_id, step_id, step_name, step_type, step_order,
                step_configuration, step_status, started_at, completed_at, duration_ms,
                input_data, output_data, input_files, output_files, files_count,
                bytes_processed, error_message, correlation_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            step.getId(),
            step.getExecutionId(),
            step.getStepId(),
            step.getStepName(),
            step.getStepType().name(),
            step.getStepOrder(),
            convertMapToJson(step.getStepConfiguration()),
            step.getStepStatus().name(),
            step.getStartedAt(),
            step.getCompletedAt(),
            step.getDurationMs(),
            convertMapToJson(step.getInputData()),
            convertMapToJson(step.getOutputData()),
            step.getInputFiles() != null ? step.getInputFiles().toArray(new String[0]) : null,
            step.getOutputFiles() != null ? step.getOutputFiles().toArray(new String[0]) : null,
            step.getFilesCount(),
            step.getBytesProcessed(),
            step.getErrorMessage(),
            step.getCorrelationId()
        );
        
        return step.getId();
    }
    
    /**
     * Update step
     */
    public void update(FlowExecutionStep step) {
        String sql = """
            UPDATE flow_execution_steps SET
                step_name = ?, step_type = ?, step_order = ?, step_configuration = ?::jsonb,
                step_status = ?, started_at = ?, completed_at = ?, duration_ms = ?,
                input_data = ?::jsonb, output_data = ?::jsonb, input_files = ?, 
                output_files = ?, files_count = ?, bytes_processed = ?, 
                error_message = ?, correlation_id = ?
            WHERE id = ?
        """;
        
        jdbcTemplate.update(sql,
            step.getStepName(),
            step.getStepType().name(),
            step.getStepOrder(),
            convertMapToJson(step.getStepConfiguration()),
            step.getStepStatus().name(),
            step.getStartedAt(),
            step.getCompletedAt(),
            step.getDurationMs(),
            convertMapToJson(step.getInputData()),
            convertMapToJson(step.getOutputData()),
            step.getInputFiles() != null ? step.getInputFiles().toArray(new String[0]) : null,
            step.getOutputFiles() != null ? step.getOutputFiles().toArray(new String[0]) : null,
            step.getFilesCount(),
            step.getBytesProcessed(),
            step.getErrorMessage(),
            step.getCorrelationId(),
            step.getId()
        );
    }
    
    /**
     * Update step status
     */
    public void updateStatus(UUID id, FlowExecutionStep.StepStatus status) {
        String sql = "UPDATE flow_execution_steps SET step_status = ? WHERE id = ?";
        jdbcTemplate.update(sql, status.name(), id);
    }
    
    /**
     * Delete step by ID
     */
    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM flow_execution_steps WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }
    
    /**
     * Delete steps by execution ID
     */
    public int deleteByExecutionId(UUID executionId) {
        String sql = "DELETE FROM flow_execution_steps WHERE execution_id = ?";
        return jdbcTemplate.update(sql, executionId);
    }
    
    /**
     * Get step statistics
     */
    public Map<String, Object> getStepStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_steps,
                COUNT(*) FILTER (WHERE step_status = 'COMPLETED') as completed_steps,
                COUNT(*) FILTER (WHERE step_status = 'FAILED') as failed_steps,
                COUNT(*) FILTER (WHERE step_status = 'RUNNING') as running_steps,
                COUNT(*) FILTER (WHERE step_status = 'PENDING') as pending_steps,
                AVG(duration_ms) as avg_duration_ms,
                SUM(files_count) as total_files_processed,
                SUM(bytes_processed) as total_bytes_processed
            FROM flow_execution_steps
        """;
        
        return jdbcTemplate.queryForMap(sql);
    }
    
    /**
     * Row mapper for FlowExecutionStep entities
     */
    private class FlowExecutionStepRowMapper implements RowMapper<FlowExecutionStep> {
        @Override
        public FlowExecutionStep mapRow(ResultSet rs, int rowNum) throws SQLException {
            FlowExecutionStep step = new FlowExecutionStep();
            
            step.setId(UUID.fromString(rs.getString("id")));
            step.setExecutionId(UUID.fromString(rs.getString("execution_id")));
            step.setStepId(rs.getString("step_id"));
            step.setStepName(rs.getString("step_name"));
            step.setStepType(FlowExecutionStep.StepType.valueOf(rs.getString("step_type")));
            step.setStepOrder(rs.getInt("step_order"));
            step.setStepConfiguration(convertJsonToMap(rs.getString("step_configuration")));
            step.setStepStatus(FlowExecutionStep.StepStatus.valueOf(rs.getString("step_status")));
            
            Timestamp startedAt = rs.getTimestamp("started_at");
            if (startedAt != null) {
                step.setStartedAt(startedAt.toLocalDateTime());
            }
            
            Timestamp completedAt = rs.getTimestamp("completed_at");
            if (completedAt != null) {
                step.setCompletedAt(completedAt.toLocalDateTime());
            }
            
            step.setDurationMs(rs.getLong("duration_ms"));
            step.setInputData(convertJsonToMap(rs.getString("input_data")));
            step.setOutputData(convertJsonToMap(rs.getString("output_data")));
            
            // Handle TEXT[] array fields
            java.sql.Array inputFilesArray = rs.getArray("input_files");
            if (inputFilesArray != null) {
                String[] inputFiles = (String[]) inputFilesArray.getArray();
                step.setInputFiles(java.util.Arrays.asList(inputFiles));
            }
            
            java.sql.Array outputFilesArray = rs.getArray("output_files");
            if (outputFilesArray != null) {
                String[] outputFiles = (String[]) outputFilesArray.getArray();
                step.setOutputFiles(java.util.Arrays.asList(outputFiles));
            }
            
            step.setFilesCount(rs.getInt("files_count"));
            step.setBytesProcessed(rs.getLong("bytes_processed"));
            step.setErrorMessage(rs.getString("error_message"));
            
            String correlationIdStr = rs.getString("correlation_id");
            if (correlationIdStr != null) {
                step.setCorrelationId(UUID.fromString(correlationIdStr));
            }
            
            return step;
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
            throw new RuntimeException("Failed to convert JSON to map", e);
        }
    }
    
    /**
     * Get aggregated statistics for a specific execution
     */
    public Map<String, Object> getExecutionStatistics(UUID executionId) {
        String sql = """
                SELECT 
                    SUM(files_count) as total_files_processed,
                    SUM(bytes_processed) as total_bytes_processed
                FROM flow_execution_steps 
                WHERE execution_id = ?
                """;
                
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("total_files_processed", rs.getInt("total_files_processed"));
            stats.put("total_bytes_processed", rs.getLong("total_bytes_processed"));
            return stats;
        }, executionId);
    }
}