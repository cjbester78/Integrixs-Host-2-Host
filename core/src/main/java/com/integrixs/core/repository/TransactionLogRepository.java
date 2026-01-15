package com.integrixs.core.repository;

import com.integrixs.shared.model.TransactionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Repository for TransactionLog entity using native SQL with Spring JDBC
 * Provides optimized queries for authentication and business transaction logging
 */
@Repository
public class TransactionLogRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(TransactionLogRepository.class);
    private final JdbcTemplate jdbcTemplate;
    
    public TransactionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Save a transaction log to the database
     */
    public TransactionLog save(TransactionLog log) {
        // Initialize timestamps if not set
        log.initializeTimestamps();
        
        // Generate UUID if not set
        if (log.getId() == null) {
            log.setId(UUID.randomUUID());
        }
        
        String sql = """
            INSERT INTO transaction_logs (
                id, timestamp, level, category, component, source, message,
                username, ip_address, user_agent, session_id, correlation_id,
                adapter_id, execution_id, file_name, details, execution_time_ms, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            log.getId(),
            log.getTimestamp(),
            log.getLevel() != null ? log.getLevel().name() : "INFO",
            log.getCategory(),
            log.getComponent(),
            log.getSource(),
            log.getMessage(),
            log.getUsername(),
            log.getIpAddress(),
            log.getUserAgent(),
            log.getSessionId(),
            log.getCorrelationId(),
            log.getAdapterId(),
            log.getExecutionId(),
            log.getFileName(),
            log.getDetails(),
            log.getExecutionTimeMs(),
            log.getCreatedAt()
        );
        
        return log;
    }
    
    /**
     * Find logs by category with limit, ordered by timestamp descending
     */
    public List<TransactionLog> findByCategoryOrderByTimestampDesc(String category, int limit) {
        String sql = "SELECT * FROM transaction_logs WHERE category = ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), category, limit);
    }
    
    /**
     * Find logs by username with limit, ordered by timestamp descending
     */
    public List<TransactionLog> findByUsernameOrderByTimestampDesc(String username, int limit) {
        String sql = "SELECT * FROM transaction_logs WHERE username = ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), username, limit);
    }
    
    /**
     * Find logs by correlation ID, ordered by timestamp ascending (for flow tracking)
     */
    public List<TransactionLog> findByCorrelationIdOrderByTimestampAsc(String correlationId) {
        String sql = "SELECT * FROM transaction_logs WHERE correlation_id = ? ORDER BY timestamp ASC";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), correlationId);
    }
    
    /**
     * Find logs by adapter ID with limit, ordered by timestamp descending
     */
    public List<TransactionLog> findByAdapterIdOrderByTimestampDesc(UUID adapterId, int limit) {
        String sql = "SELECT * FROM transaction_logs WHERE adapter_id = ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), adapterId, limit);
    }
    
    /**
     * Find recent authentication logs (AUTH_% categories) since a specific time
     */
    public List<TransactionLog> findRecentAuthenticationLogs(LocalDateTime since, int limit) {
        String sql = "SELECT * FROM transaction_logs WHERE category LIKE 'AUTH_%' AND timestamp >= ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), since, limit);
    }
    
    /**
     * Find recent error logs since a specific time
     */
    public List<TransactionLog> findRecentErrors(LocalDateTime since, int limit) {
        String sql = "SELECT * FROM transaction_logs WHERE level = 'ERROR' AND timestamp >= ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), since, limit);
    }
    
    /**
     * Find authentication failures for a specific IP address within a time range
     */
    public List<TransactionLog> findAuthenticationFailuresByIp(String ipAddress, LocalDateTime since) {
        String sql = "SELECT * FROM transaction_logs WHERE category = 'AUTH_FAILED' AND ip_address = ?::inet AND timestamp >= ? ORDER BY timestamp DESC";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), ipAddress, since);
    }
    
    /**
     * Find authentication failures for a specific username within a time range
     */
    public List<TransactionLog> findAuthenticationFailuresByUsername(String username, LocalDateTime since) {
        String sql = "SELECT * FROM transaction_logs WHERE category = 'AUTH_FAILED' AND username = ? AND timestamp >= ? ORDER BY timestamp DESC";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), username, since);
    }
    
    /**
     * Count authentication failures for an IP address within a time range
     */
    public long countAuthenticationFailuresByIp(String ipAddress, LocalDateTime since) {
        String sql = "SELECT COUNT(*) FROM transaction_logs WHERE category = 'AUTH_FAILED' AND ip_address = ?::inet AND timestamp >= ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, ipAddress, since);
        return count != null ? count : 0;
    }
    
    /**
     * Count authentication failures for a username within a time range
     */
    public long countAuthenticationFailuresByUsername(String username, LocalDateTime since) {
        String sql = "SELECT COUNT(*) FROM transaction_logs WHERE category = 'AUTH_FAILED' AND username = ? AND timestamp >= ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, username, since);
        return count != null ? count : 0;
    }
    
    /**
     * Find file processing logs by file name
     */
    public List<TransactionLog> findFileProcessingLogs(String fileName) {
        String sql = "SELECT * FROM transaction_logs WHERE file_name = ? ORDER BY timestamp ASC";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), fileName);
    }
    
    /**
     * Find logs by execution ID for flow tracking
     */
    public List<TransactionLog> findByExecutionIdOrderByTimestampAsc(UUID executionId) {
        String sql = "SELECT * FROM transaction_logs WHERE execution_id = ? ORDER BY timestamp ASC";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), executionId);
    }
    
    /**
     * Get authentication statistics for dashboard
     */
    public List<Object[]> getAuthenticationStatistics(LocalDateTime since) {
        String sql = "SELECT category, COUNT(*) FROM transaction_logs WHERE category LIKE 'AUTH_%' AND timestamp >= ? GROUP BY category";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString("category"),
            rs.getLong("count")
        }, since);
    }
    
    /**
     * Find recent transaction logs for activity feed
     */
    public List<TransactionLog> findRecentTransactionLogs(int limit) {
        String sql = "SELECT * FROM transaction_logs ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, new TransactionLogRowMapper(), limit);
    }

    /**
     * Delete old logs before a specific timestamp (for log retention)
     */
    public void deleteOldLogs(LocalDateTime before) {
        String sql = "DELETE FROM transaction_logs WHERE timestamp < ?";
        jdbcTemplate.update(sql, before);
    }

    /**
     * Get recent transaction logs for frontend display with filtering and search
     * Returns Map<String, Object> for direct JSON serialization
     */
    public List<Map<String, Object>> getRecentLogsForApi(int limit, String level, String category, String search) {
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, timestamp, level, category, component, source, message, ");
            sql.append("username, ip_address, user_agent, session_id, correlation_id, adapter_id, ");
            sql.append("execution_id, file_name, details, execution_time_ms, created_at ");
            sql.append("FROM transaction_logs WHERE 1=1 ");
            
            // Add filters
            if (level != null && !level.equals("ALL")) {
                sql.append("AND level = ? ");
            }
            
            if (category != null && !category.equals("ALL")) {
                sql.append("AND category = ? ");
            }
            
            if (search != null && !search.trim().isEmpty()) {
                sql.append("AND (message ILIKE ? OR username ILIKE ? OR correlation_id ILIKE ?) ");
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
            
            return jdbcTemplate.query(sql.toString(), transactionLogApiRowMapper(), params);
            
        } catch (Exception e) {
            logger.error("Error retrieving transaction logs for API", e);
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
    
    private RowMapper<Map<String, Object>> transactionLogApiRowMapper() {
        return new RowMapper<Map<String, Object>>() {
            @Override
            public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
                Map<String, Object> log = new HashMap<>();
                
                log.put("id", rs.getString("id"));
                log.put("timestamp", rs.getTimestamp("timestamp").toLocalDateTime().toString());
                log.put("level", rs.getString("level"));
                log.put("category", rs.getString("category"));
                log.put("component", rs.getString("component"));
                log.put("source", rs.getString("source"));
                log.put("message", rs.getString("message"));
                log.put("username", rs.getString("username"));
                log.put("ipAddress", rs.getString("ip_address"));
                log.put("userAgent", rs.getString("user_agent"));
                log.put("sessionId", rs.getString("session_id"));
                log.put("correlationId", rs.getString("correlation_id"));
                log.put("adapterId", rs.getString("adapter_id"));
                log.put("executionId", rs.getString("execution_id"));
                log.put("fileName", rs.getString("file_name"));
                log.put("details", rs.getString("details"));
                log.put("executionTimeMs", rs.getLong("execution_time_ms"));
                log.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                
                return log;
            }
        };
    }
    
    /**
     * Row mapper for TransactionLog entity
     */
    private static class TransactionLogRowMapper implements RowMapper<TransactionLog> {
        @Override
        public TransactionLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            TransactionLog log = new TransactionLog();
            
            log.setId(UUID.fromString(rs.getString("id")));
            log.setTimestamp(rs.getTimestamp("timestamp").toLocalDateTime());
            log.setLevel(TransactionLog.LogLevel.valueOf(rs.getString("level")));
            log.setCategory(rs.getString("category"));
            log.setComponent(rs.getString("component"));
            log.setSource(rs.getString("source"));
            log.setMessage(rs.getString("message"));
            log.setUsername(rs.getString("username"));
            log.setIpAddress(rs.getString("ip_address"));
            log.setUserAgent(rs.getString("user_agent"));
            log.setSessionId(rs.getString("session_id"));
            log.setCorrelationId(rs.getString("correlation_id"));
            
            String adapterIdStr = rs.getString("adapter_id");
            if (adapterIdStr != null) {
                log.setAdapterId(UUID.fromString(adapterIdStr));
            }
            
            String executionIdStr = rs.getString("execution_id");
            if (executionIdStr != null) {
                log.setExecutionId(UUID.fromString(executionIdStr));
            }
            
            log.setFileName(rs.getString("file_name"));
            log.setDetails(rs.getString("details"));
            log.setExecutionTimeMs(rs.getLong("execution_time_ms"));
            log.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            
            return log;
        }
    }
}