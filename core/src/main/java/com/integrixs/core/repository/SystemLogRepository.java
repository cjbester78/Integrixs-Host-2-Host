package com.integrixs.core.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.integrixs.shared.model.SystemLog;
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
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for system logs that duplicates file-based logging to database
 * Provides categorized logging for separate views (adapter execution, flow execution, etc.)
 */
@Repository
public class SystemLogRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public SystemLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Insert a system log entry
     * This method should be called by the database appender to store logs
     */
    public void insertLog(SystemLog log) {
        String sql = """
            INSERT INTO system_logs (
                id, timestamp, log_level, log_category, logger_name, thread_name,
                message, formatted_message, correlation_id, session_id, user_id,
                adapter_id, adapter_name, flow_id, flow_name, execution_id,
                request_id, request_method, request_uri, remote_address, user_agent,
                application_name, environment, server_hostname,
                exception_class, exception_message, stack_trace,
                mdc_data, marker, execution_time_ms, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            log.getId(),
            log.getTimestamp(),
            log.getLogLevel() != null ? log.getLogLevel().name() : null,
            log.getLogCategory() != null ? log.getLogCategory().name() : SystemLog.LogCategory.SYSTEM.name(),
            log.getLoggerName(),
            log.getThreadName(),
            log.getMessage(),
            log.getFormattedMessage(),
            log.getCorrelationId(),
            log.getSessionId(),
            log.getUserId(),
            log.getAdapterId(),
            log.getAdapterName(),
            log.getFlowId(),
            log.getFlowName(),
            log.getExecutionId(),
            log.getRequestId(),
            log.getRequestMethod(),
            log.getRequestUri(),
            log.getRemoteAddress(),
            log.getUserAgent(),
            log.getApplicationName(),
            log.getEnvironment(),
            log.getServerHostname(),
            log.getExceptionClass(),
            log.getExceptionMessage(),
            log.getStackTrace(),
            convertMapToJson(log.getMdcData()),
            log.getMarker(),
            log.getExecutionTimeMs(),
            log.getCreatedAt()
        );
    }
    
    /**
     * Get all logs with pagination and filtering
     */
    public List<SystemLog> findLogs(SystemLog.LogCategory category, SystemLog.LogLevel minLevel, 
                                  int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, timestamp, log_level, log_category, logger_name, thread_name,
                   message, formatted_message, correlation_id, session_id, user_id,
                   adapter_id, adapter_name, flow_id, flow_name, execution_id,
                   request_id, request_method, request_uri, remote_address, user_agent,
                   application_name, environment, server_hostname,
                   exception_class, exception_message, stack_trace,
                   mdc_data, marker, execution_time_ms, created_at
            FROM system_logs
            WHERE 1=1
            """);
        
        if (category != null) {
            sql.append(" AND log_category = ?");
        }
        if (minLevel != null) {
            sql.append(" AND log_level IN (");
            // Include all levels from minLevel and above
            SystemLog.LogLevel[] levels = SystemLog.LogLevel.values();
            boolean found = false;
            for (SystemLog.LogLevel level : levels) {
                if (found || level == minLevel) {
                    if (found) sql.append(", ");
                    sql.append("'").append(level.name()).append("'");
                    found = true;
                }
            }
            sql.append(")");
        }
        
        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        
        return jdbcTemplate.query(sql.toString(), new SystemLogRowMapper(),
            buildParams(category, minLevel, limit, offset));
    }
    
    /**
     * Get logs by adapter
     */
    public List<SystemLog> findAdapterLogs(UUID adapterId, SystemLog.LogLevel minLevel, 
                                         int limit, int offset) {
        String sql = """
            SELECT id, timestamp, log_level, log_category, logger_name, thread_name,
                   message, formatted_message, correlation_id, session_id, user_id,
                   adapter_id, adapter_name, flow_id, flow_name, execution_id,
                   request_id, request_method, request_uri, remote_address, user_agent,
                   application_name, environment, server_hostname,
                   exception_class, exception_message, stack_trace,
                   mdc_data, marker, execution_time_ms, created_at
            FROM system_logs
            WHERE log_category = 'ADAPTER_EXECUTION' AND adapter_id = ?
            """ + (minLevel != null ? " AND log_level IN " + buildLevelFilter(minLevel) : "") + """
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """;
        
        if (minLevel != null) {
            return jdbcTemplate.query(sql, new SystemLogRowMapper(), adapterId, limit, offset);
        } else {
            return jdbcTemplate.query(sql, new SystemLogRowMapper(), adapterId, limit, offset);
        }
    }
    
    /**
     * Get logs by flow
     */
    public List<SystemLog> findFlowLogs(UUID flowId, SystemLog.LogLevel minLevel, 
                                      int limit, int offset) {
        String sql = """
            SELECT id, timestamp, log_level, log_category, logger_name, thread_name,
                   message, formatted_message, correlation_id, session_id, user_id,
                   adapter_id, adapter_name, flow_id, flow_name, execution_id,
                   request_id, request_method, request_uri, remote_address, user_agent,
                   application_name, environment, server_hostname,
                   exception_class, exception_message, stack_trace,
                   mdc_data, marker, execution_time_ms, created_at
            FROM system_logs
            WHERE log_category = 'FLOW_EXECUTION' AND flow_id = ?
            """ + (minLevel != null ? " AND log_level IN " + buildLevelFilter(minLevel) : "") + """
            ORDER BY timestamp DESC
            LIMIT ? OFFSET ?
            """;
        
        if (minLevel != null) {
            return jdbcTemplate.query(sql, new SystemLogRowMapper(), flowId, limit, offset);
        } else {
            return jdbcTemplate.query(sql, new SystemLogRowMapper(), flowId, limit, offset);
        }
    }
    
    /**
     * Get logs by execution ID
     */
    public List<SystemLog> findExecutionLogs(UUID executionId, int limit, int offset) {
        String sql = """
            SELECT id, timestamp, log_level, log_category, logger_name, thread_name,
                   message, formatted_message, correlation_id, session_id, user_id,
                   adapter_id, adapter_name, flow_id, flow_name, execution_id,
                   request_id, request_method, request_uri, remote_address, user_agent,
                   application_name, environment, server_hostname,
                   exception_class, exception_message, stack_trace,
                   mdc_data, marker, execution_time_ms, created_at
            FROM system_logs
            WHERE execution_id = ?
            ORDER BY timestamp ASC
            LIMIT ? OFFSET ?
            """;
        
        return jdbcTemplate.query(sql, new SystemLogRowMapper(), executionId, limit, offset);
    }
    
    /**
     * Count logs with filters
     */
    public long countLogs(SystemLog.LogCategory category, SystemLog.LogLevel minLevel) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM system_logs WHERE 1=1");
        
        if (category != null) {
            sql.append(" AND log_category = ?");
        }
        if (minLevel != null) {
            sql.append(" AND log_level IN ").append(buildLevelFilter(minLevel));
        }
        
        Object[] params = buildParams(category, minLevel);
        if (params.length > 0) {
            return jdbcTemplate.queryForObject(sql.toString(), Long.class, params);
        } else {
            return jdbcTemplate.queryForObject(sql.toString(), Long.class);
        }
    }
    
    /**
     * Get recent error logs
     */
    public List<SystemLog> findRecentErrors(int limit) {
        String sql = """
            SELECT id, timestamp, log_level, log_category, logger_name, thread_name,
                   message, formatted_message, correlation_id, session_id, user_id,
                   adapter_id, adapter_name, flow_id, flow_name, execution_id,
                   request_id, request_method, request_uri, remote_address, user_agent,
                   application_name, environment, server_hostname,
                   exception_class, exception_message, stack_trace,
                   mdc_data, marker, execution_time_ms, created_at
            FROM system_logs
            WHERE log_level IN ('ERROR', 'FATAL')
            ORDER BY timestamp DESC
            LIMIT ?
            """;
        
        return jdbcTemplate.query(sql, new SystemLogRowMapper(), limit);
    }
    
    /**
     * Search logs by message ID for complete traceability
     */
    public List<SystemLog> searchByMessageId(String messageId) {
        String sql = """
            SELECT id, timestamp, log_level, log_category, logger_name, thread_name,
                   message, formatted_message, correlation_id, session_id, user_id,
                   adapter_id, adapter_name, flow_id, flow_name, execution_id,
                   request_id, request_method, request_uri, remote_address, user_agent,
                   application_name, environment, server_hostname,
                   exception_class, exception_message, stack_trace,
                   mdc_data, marker, execution_time_ms, created_at
            FROM system_logs
            WHERE mdc_data::text LIKE ? OR message LIKE ? OR formatted_message LIKE ?
            ORDER BY timestamp ASC
            """;
        
        String searchPattern = "%" + messageId + "%";
        return jdbcTemplate.query(sql, new SystemLogRowMapper(), 
                                searchPattern, searchPattern, searchPattern);
    }
    
    /**
     * Search logs by correlation ID for related operations
     */
    public List<SystemLog> searchByCorrelationId(UUID correlationId) {
        String sql = """
            SELECT id, timestamp, log_level, log_category, logger_name, thread_name,
                   message, formatted_message, correlation_id, session_id, user_id,
                   adapter_id, adapter_name, flow_id, flow_name, execution_id,
                   request_id, request_method, request_uri, remote_address, user_agent,
                   application_name, environment, server_hostname,
                   exception_class, exception_message, stack_trace,
                   mdc_data, marker, execution_time_ms, created_at
            FROM system_logs
            WHERE correlation_id = ?
            ORDER BY timestamp ASC
            """;
        
        return jdbcTemplate.query(sql, new SystemLogRowMapper(), correlationId);
    }
    
    /**
     * Delete old logs (for maintenance)
     */
    public int deleteOldLogs(LocalDateTime beforeDate) {
        String sql = "DELETE FROM system_logs WHERE timestamp < ?";
        return jdbcTemplate.update(sql, beforeDate);
    }

    /**
     * Get recent logs for frontend display with filtering and search
     * Returns Map<String, Object> for direct JSON serialization
     */
    public List<Map<String, Object>> getRecentLogsForApi(int limit, String level, String category, String search) {
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, timestamp, log_level, log_category, logger_name, thread_name, ");
            sql.append("message, formatted_message, correlation_id, user_id, adapter_id, adapter_name, ");
            sql.append("flow_id, flow_name, execution_id, request_method, request_uri, remote_address, ");
            sql.append("application_name, environment, exception_class, exception_message, stack_trace ");
            sql.append("FROM system_logs WHERE 1=1 ");
            
            // Add filters
            if (level != null && !level.equals("ALL")) {
                sql.append("AND log_level = ? ");
            }
            
            if (category != null && !category.equals("ALL")) {
                sql.append("AND log_category = ? ");
            }
            
            if (search != null && !search.trim().isEmpty()) {
                sql.append("AND (message ILIKE ? OR logger_name ILIKE ? OR correlation_id::text ILIKE ?) ");
            }
            
            sql.append("ORDER BY timestamp DESC LIMIT ?");
            
            // Prepare parameters
            Object[] params = new Object[getParameterCount(level, category, search) + 1];
            int paramIndex = 0;
            
            if (level != null && !level.equals("ALL")) {
                params[paramIndex++] = level;
            }
            
            if (category != null && !category.equals("ALL")) {
                params[paramIndex++] = category;
            }
            
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim() + "%";
                params[paramIndex++] = searchPattern;
                params[paramIndex++] = searchPattern;
                params[paramIndex++] = searchPattern;
            }
            
            params[paramIndex] = limit;
            
            return jdbcTemplate.query(sql.toString(), systemLogApiRowMapper(), params);
            
        } catch (Exception e) {
            System.err.println("Error retrieving system logs for API: " + e.getMessage());
            return List.of();
        }
    }

    private int getParameterCount(String level, String category, String search) {
        int count = 0;
        if (level != null && !level.equals("ALL")) count++;
        if (category != null && !category.equals("ALL")) count++;
        if (search != null && !search.trim().isEmpty()) count += 3; // search pattern used 3 times
        return count;
    }
    
    private RowMapper<Map<String, Object>> systemLogApiRowMapper() {
        return new RowMapper<Map<String, Object>>() {
            @Override
            public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
                Map<String, Object> log = new HashMap<>();
                
                log.put("id", rs.getString("id"));
                log.put("timestamp", rs.getTimestamp("timestamp").toLocalDateTime().toString());
                log.put("logLevel", rs.getString("log_level"));
                log.put("logCategory", rs.getString("log_category"));
                log.put("loggerName", rs.getString("logger_name"));
                log.put("threadName", rs.getString("thread_name"));
                log.put("message", rs.getString("message"));
                log.put("formattedMessage", rs.getString("formatted_message"));
                log.put("correlationId", rs.getString("correlation_id"));
                log.put("userId", rs.getString("user_id"));
                log.put("adapterId", rs.getString("adapter_id"));
                log.put("adapterName", rs.getString("adapter_name"));
                log.put("flowId", rs.getString("flow_id"));
                log.put("flowName", rs.getString("flow_name"));
                log.put("executionId", rs.getString("execution_id"));
                log.put("requestMethod", rs.getString("request_method"));
                log.put("requestUri", rs.getString("request_uri"));
                log.put("remoteAddress", rs.getString("remote_address"));
                log.put("applicationName", rs.getString("application_name"));
                log.put("environment", rs.getString("environment"));
                log.put("exceptionClass", rs.getString("exception_class"));
                log.put("exceptionMessage", rs.getString("exception_message"));
                log.put("stackTrace", rs.getString("stack_trace"));
                
                return log;
            }
        };
    }
    
    /**
     * Row mapper for SystemLog entities
     */
    private static class SystemLogRowMapper implements RowMapper<SystemLog> {
        @Override
        public SystemLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            SystemLog log = new SystemLog();
            
            log.setId(UUID.fromString(rs.getString("id")));
            
            Timestamp timestamp = rs.getTimestamp("timestamp");
            if (timestamp != null) {
                log.setTimestamp(timestamp.toLocalDateTime());
            }
            
            String logLevel = rs.getString("log_level");
            if (logLevel != null) {
                log.setLogLevel(SystemLog.LogLevel.valueOf(logLevel));
            }
            
            String logCategory = rs.getString("log_category");
            if (logCategory != null) {
                log.setLogCategory(SystemLog.LogCategory.valueOf(logCategory));
            }
            
            log.setLoggerName(rs.getString("logger_name"));
            log.setThreadName(rs.getString("thread_name"));
            log.setMessage(rs.getString("message"));
            log.setFormattedMessage(rs.getString("formatted_message"));
            
            String correlationId = rs.getString("correlation_id");
            if (correlationId != null) {
                log.setCorrelationId(UUID.fromString(correlationId));
            }
            
            log.setSessionId(rs.getString("session_id"));
            
            String userId = rs.getString("user_id");
            if (userId != null) {
                log.setUserId(UUID.fromString(userId));
            }
            
            String adapterId = rs.getString("adapter_id");
            if (adapterId != null) {
                log.setAdapterId(UUID.fromString(adapterId));
            }
            
            log.setAdapterName(rs.getString("adapter_name"));
            
            String flowId = rs.getString("flow_id");
            if (flowId != null) {
                log.setFlowId(UUID.fromString(flowId));
            }
            
            log.setFlowName(rs.getString("flow_name"));
            
            String executionId = rs.getString("execution_id");
            if (executionId != null) {
                log.setExecutionId(UUID.fromString(executionId));
            }
            
            log.setRequestId(rs.getString("request_id"));
            log.setRequestMethod(rs.getString("request_method"));
            log.setRequestUri(rs.getString("request_uri"));
            log.setRemoteAddress(rs.getString("remote_address"));
            log.setUserAgent(rs.getString("user_agent"));
            log.setApplicationName(rs.getString("application_name"));
            log.setEnvironment(rs.getString("environment"));
            log.setServerHostname(rs.getString("server_hostname"));
            log.setExceptionClass(rs.getString("exception_class"));
            log.setExceptionMessage(rs.getString("exception_message"));
            log.setStackTrace(rs.getString("stack_trace"));
            log.setMarker(rs.getString("marker"));
            
            Long executionTimeMs = rs.getObject("execution_time_ms", Long.class);
            log.setExecutionTimeMs(executionTimeMs);
            
            // Handle MDC data JSON
            String mdcDataJson = rs.getString("mdc_data");
            if (mdcDataJson != null && !mdcDataJson.trim().isEmpty()) {
                // This would need proper JSON parsing implementation
                // For now, we'll skip it to avoid complexity
            }
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                log.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            return log;
        }
    }
    
    // Helper methods
    private Object[] buildParams(SystemLog.LogCategory category, SystemLog.LogLevel minLevel, Object... additional) {
        int paramCount = (category != null ? 1 : 0) + (minLevel != null ? 0 : 0) + additional.length;
        Object[] params = new Object[paramCount + additional.length];
        int index = 0;
        
        if (category != null) {
            params[index++] = category.name();
        }
        
        for (Object param : additional) {
            params[index++] = param;
        }
        
        return params;
    }
    
    private String buildLevelFilter(SystemLog.LogLevel minLevel) {
        StringBuilder filter = new StringBuilder("(");
        SystemLog.LogLevel[] levels = SystemLog.LogLevel.values();
        boolean found = false;
        boolean first = true;
        
        for (SystemLog.LogLevel level : levels) {
            if (found || level == minLevel) {
                if (!first) filter.append(", ");
                filter.append("'").append(level.name()).append("'");
                first = false;
                found = true;
            }
        }
        filter.append(")");
        return filter.toString();
    }
    
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}