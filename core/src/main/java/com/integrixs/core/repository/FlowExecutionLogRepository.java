package com.integrixs.core.repository;

import com.integrixs.shared.model.FlowExecutionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for FlowExecutionLog entity operations
 * Provides comprehensive logging storage and retrieval for flow monitoring
 */
@Repository
public class FlowExecutionLogRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowExecutionLogRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public FlowExecutionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Save a flow execution log entry
     */
    public FlowExecutionLog save(FlowExecutionLog log) {
        logger.debug("Saving execution log: {} - {}", log.getLevel(), log.getMessage());
        
        String sql = """
            INSERT INTO flow_execution_logs (
                id, execution_id, step_id, level, timestamp, category,
                message, details, class_name, method_name, line_number,
                directory, file_name, file_size, correlation_id, thread_name
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            log.getId(),
            log.getExecutionId(),
            log.getStepId(),
            log.getLevel().name(),
            log.getTimestamp(),
            log.getCategory(),
            log.getMessage(),
            log.getDetails(),
            log.getClassName(),
            log.getMethodName(),
            log.getLineNumber(),
            log.getDirectory(),
            log.getFileName(),
            log.getFileSize(),
            log.getCorrelationId(),
            log.getThreadName()
        );
        
        return log;
    }
    
    /**
     * Find logs by execution ID
     */
    public List<FlowExecutionLog> findByExecutionId(UUID executionId) {
        logger.debug("Finding logs for execution: {}", executionId);
        
        String sql = """
            SELECT * FROM flow_execution_logs 
            WHERE execution_id = ? 
            ORDER BY timestamp ASC
            """;
        
        return jdbcTemplate.query(sql, new FlowExecutionLogRowMapper(), executionId);
    }
    
    /**
     * Find logs by execution ID with filtering
     */
    public List<FlowExecutionLog> findByExecutionIdWithFilter(UUID executionId, String levelFilter, 
                                                              String messageFilter, int limit) {
        logger.debug("Finding filtered logs for execution: {} (level: {}, filter: {}, limit: {})", 
                    executionId, levelFilter, messageFilter, limit);
        
        StringBuilder sql = new StringBuilder("""
            SELECT * FROM flow_execution_logs 
            WHERE execution_id = ?
            """);
        
        // Add level filter
        if (levelFilter != null && !levelFilter.equals("all")) {
            sql.append(" AND level = '").append(levelFilter.toUpperCase()).append("'");
        }
        
        // Add message filter
        if (messageFilter != null && !messageFilter.trim().isEmpty()) {
            sql.append(" AND (message ILIKE '%").append(messageFilter.trim()).append("%'")
               .append(" OR details ILIKE '%").append(messageFilter.trim()).append("%'")
               .append(" OR file_name ILIKE '%").append(messageFilter.trim()).append("%')");
        }
        
        sql.append(" ORDER BY timestamp ASC LIMIT ").append(limit);
        
        return jdbcTemplate.query(sql.toString(), new FlowExecutionLogRowMapper(), executionId);
    }
    
    /**
     * Find logs by step ID
     */
    public List<FlowExecutionLog> findByStepId(UUID stepId) {
        logger.debug("Finding logs for step: {}", stepId);
        
        String sql = """
            SELECT * FROM flow_execution_logs 
            WHERE step_id = ? 
            ORDER BY timestamp ASC
            """;
        
        return jdbcTemplate.query(sql, new FlowExecutionLogRowMapper(), stepId);
    }
    
    /**
     * Find recent logs across all executions
     */
    public List<FlowExecutionLog> findRecentLogs(int limit) {
        logger.debug("Finding {} most recent logs", limit);
        
        String sql = """
            SELECT * FROM flow_execution_logs 
            ORDER BY timestamp DESC 
            LIMIT ?
            """;
        
        return jdbcTemplate.query(sql, new FlowExecutionLogRowMapper(), limit);
    }
    
    /**
     * Find error logs for a specific execution
     */
    public List<FlowExecutionLog> findErrorLogsByExecutionId(UUID executionId) {
        logger.debug("Finding error logs for execution: {}", executionId);
        
        String sql = """
            SELECT * FROM flow_execution_logs 
            WHERE execution_id = ? AND level = 'ERROR'
            ORDER BY timestamp ASC
            """;
        
        return jdbcTemplate.query(sql, new FlowExecutionLogRowMapper(), executionId);
    }
    
    /**
     * Count logs by execution ID and level
     */
    public int countByExecutionIdAndLevel(UUID executionId, FlowExecutionLog.LogLevel level) {
        logger.debug("Counting {} logs for execution: {}", level, executionId);
        
        String sql = """
            SELECT COUNT(*) FROM flow_execution_logs 
            WHERE execution_id = ? AND level = ?
            """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, executionId, level.name());
        return count != null ? count : 0;
    }
    
    /**
     * Delete logs older than specified date
     */
    public int deleteLogsOlderThan(LocalDateTime cutoffDate) {
        logger.info("Deleting logs older than: {}", cutoffDate);
        
        String sql = """
            DELETE FROM flow_execution_logs 
            WHERE timestamp < ?
            """;
        
        int deletedCount = jdbcTemplate.update(sql, cutoffDate);
        logger.info("Deleted {} old log entries", deletedCount);
        return deletedCount;
    }
    
    /**
     * Delete logs for a specific execution
     */
    public int deleteByExecutionId(UUID executionId) {
        logger.info("Deleting logs for execution: {}", executionId);
        
        String sql = """
            DELETE FROM flow_execution_logs 
            WHERE execution_id = ?
            """;
        
        int deletedCount = jdbcTemplate.update(sql, executionId);
        logger.info("Deleted {} logs for execution: {}", deletedCount, executionId);
        return deletedCount;
    }
    
    /**
     * Get logging statistics
     */
    public LoggingStatistics getLoggingStatistics() {
        logger.debug("Getting logging statistics");
        
        String sql = """
            SELECT 
                COUNT(*) as total_logs,
                COUNT(CASE WHEN level = 'ERROR' THEN 1 END) as error_logs,
                COUNT(CASE WHEN level = 'WARN' THEN 1 END) as warn_logs,
                COUNT(CASE WHEN level = 'INFO' THEN 1 END) as info_logs,
                COUNT(CASE WHEN level = 'DEBUG' THEN 1 END) as debug_logs,
                COUNT(CASE WHEN level = 'TRACE' THEN 1 END) as trace_logs,
                MIN(timestamp) as oldest_log,
                MAX(timestamp) as newest_log
            FROM flow_execution_logs
            """;
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            LoggingStatistics stats = new LoggingStatistics();
            stats.totalLogs = rs.getLong("total_logs");
            stats.errorLogs = rs.getLong("error_logs");
            stats.warnLogs = rs.getLong("warn_logs");
            stats.infoLogs = rs.getLong("info_logs");
            stats.debugLogs = rs.getLong("debug_logs");
            stats.traceLogs = rs.getLong("trace_logs");
            stats.oldestLog = rs.getTimestamp("oldest_log") != null ? 
                rs.getTimestamp("oldest_log").toLocalDateTime() : null;
            stats.newestLog = rs.getTimestamp("newest_log") != null ? 
                rs.getTimestamp("newest_log").toLocalDateTime() : null;
            return stats;
        });
    }
    
    /**
     * Row mapper for FlowExecutionLog
     */
    private static class FlowExecutionLogRowMapper implements RowMapper<FlowExecutionLog> {
        @Override
        public FlowExecutionLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            FlowExecutionLog log = new FlowExecutionLog();
            
            log.setId(UUID.fromString(rs.getString("id")));
            log.setExecutionId(UUID.fromString(rs.getString("execution_id")));
            
            String stepIdStr = rs.getString("step_id");
            if (stepIdStr != null) {
                log.setStepId(UUID.fromString(stepIdStr));
            }
            
            log.setLevel(FlowExecutionLog.LogLevel.valueOf(rs.getString("level")));
            log.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
            log.setCategory(rs.getString("category"));
            log.setMessage(rs.getString("message"));
            log.setDetails(rs.getString("details"));
            log.setClassName(rs.getString("class_name"));
            log.setMethodName(rs.getString("method_name"));
            log.setLineNumber(rs.getObject("line_number", Integer.class));
            log.setDirectory(rs.getString("directory"));
            log.setFileName(rs.getString("file_name"));
            log.setFileSize(rs.getObject("file_size", Long.class));
            
            String correlationIdStr = rs.getString("correlation_id");
            if (correlationIdStr != null) {
                log.setCorrelationId(UUID.fromString(correlationIdStr));
            }
            
            log.setThreadName(rs.getString("thread_name"));
            
            return log;
        }
    }
    
    /**
     * Statistics holder for logging metrics
     */
    public static class LoggingStatistics {
        public long totalLogs;
        public long errorLogs;
        public long warnLogs;
        public long infoLogs;
        public long debugLogs;
        public long traceLogs;
        public LocalDateTime oldestLog;
        public LocalDateTime newestLog;
    }
}