package com.integrixs.core.repository;

import com.integrixs.shared.model.UserManagementError;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for UserManagementError entity using native SQL with Spring JDBC
 * Provides optimized queries for security error tracking and threat analysis
 */
@Repository
public class UserManagementErrorRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public UserManagementErrorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Save a user management error to the database
     */
    public UserManagementError save(UserManagementError error) {
        // Initialize timestamps if not set
        error.initializeTimestamps();
        
        // Generate UUID if not set
        if (error.getId() == null) {
            error.setId(UUID.randomUUID());
        }
        
        String sql = """
            INSERT INTO user_management_errors (
                id, error_type, error_code, action, error_message,
                username, ip_address, user_agent, threat_level,
                transaction_log_id, occurred_at, is_resolved, resolution_notes
            ) VALUES (?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            error.getId(),
            error.getErrorType().name(),
            error.getErrorCode(),
            error.getAction(),
            error.getErrorMessage(),
            error.getUsername(),
            error.getIpAddress(),
            error.getUserAgent(),
            error.getThreatLevel().name(),
            error.getTransactionLogId(),
            error.getOccurredAt(),
            error.isResolved(),
            error.getResolutionNotes()
        );
        
        return error;
    }
    
    /**
     * Update error resolution status
     */
    public void resolveError(UUID errorId, String resolutionNotes) {
        String sql = "UPDATE user_management_errors SET is_resolved = true, resolution_notes = ?, resolved_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, resolutionNotes, errorId);
    }
    
    /**
     * Find unresolved errors with limit, ordered by occurrence time descending
     */
    public List<UserManagementError> findUnresolvedErrors(int limit) {
        String sql = "SELECT * FROM user_management_errors WHERE is_resolved = false ORDER BY occurred_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new UserManagementErrorRowMapper(), limit);
    }
    
    /**
     * Find errors by threat level
     */
    public List<UserManagementError> findByThreatLevel(UserManagementError.ThreatLevel threatLevel, int limit) {
        String sql = "SELECT * FROM user_management_errors WHERE threat_level = ? ORDER BY occurred_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new UserManagementErrorRowMapper(), threatLevel.name(), limit);
    }
    
    /**
     * Find errors by username within a time range
     */
    public List<UserManagementError> findByUsernameSince(String username, LocalDateTime since) {
        String sql = "SELECT * FROM user_management_errors WHERE username = ? AND occurred_at >= ? ORDER BY occurred_at DESC";
        return jdbcTemplate.query(sql, new UserManagementErrorRowMapper(), username, since);
    }
    
    /**
     * Find errors by IP address within a time range
     */
    public List<UserManagementError> findByIpAddressSince(String ipAddress, LocalDateTime since) {
        String sql = "SELECT * FROM user_management_errors WHERE ip_address = ?::inet AND occurred_at >= ? ORDER BY occurred_at DESC";
        return jdbcTemplate.query(sql, new UserManagementErrorRowMapper(), ipAddress, since);
    }
    
    /**
     * Find errors by error type within a time range
     */
    public List<UserManagementError> findByErrorTypeSince(UserManagementError.ErrorType errorType, LocalDateTime since) {
        String sql = "SELECT * FROM user_management_errors WHERE error_type = ? AND occurred_at >= ? ORDER BY occurred_at DESC";
        return jdbcTemplate.query(sql, new UserManagementErrorRowMapper(), errorType.name(), since);
    }
    
    /**
     * Count errors by IP address within a time range
     */
    public long countErrorsByIpAddress(String ipAddress, LocalDateTime since) {
        String sql = "SELECT COUNT(*) FROM user_management_errors WHERE ip_address = ?::inet AND occurred_at >= ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, ipAddress, since);
        return count != null ? count : 0;
    }
    
    /**
     * Count errors by username within a time range
     */
    public long countErrorsByUsername(String username, LocalDateTime since) {
        String sql = "SELECT COUNT(*) FROM user_management_errors WHERE username = ? AND occurred_at >= ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, username, since);
        return count != null ? count : 0;
    }
    
    /**
     * Find high/critical threat level errors
     */
    public List<UserManagementError> findHighThreatErrors(LocalDateTime since, int limit) {
        String sql = "SELECT * FROM user_management_errors WHERE threat_level IN ('HIGH', 'CRITICAL') AND occurred_at >= ? ORDER BY occurred_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new UserManagementErrorRowMapper(), since, limit);
    }
    
    /**
     * Get error statistics by type
     */
    public List<Object[]> getErrorStatisticsByType(LocalDateTime since) {
        String sql = "SELECT error_type, threat_level, COUNT(*) FROM user_management_errors WHERE occurred_at >= ? GROUP BY error_type, threat_level";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString("error_type"),
            rs.getString("threat_level"),
            rs.getLong("count")
        }, since);
    }
    
    /**
     * Get error statistics by IP address
     */
    public List<Object[]> getErrorStatisticsByIpAddress(LocalDateTime since) {
        String sql = "SELECT ip_address, COUNT(*) as error_count FROM user_management_errors WHERE occurred_at >= ? GROUP BY ip_address ORDER BY error_count DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString("ip_address"),
            rs.getLong("error_count")
        }, since);
    }
    
    /**
     * Find errors with correlation to transaction logs
     */
    public List<UserManagementError> findErrorsWithTransactionLogs(int limit) {
        String sql = "SELECT * FROM user_management_errors WHERE transaction_log_id IS NOT NULL ORDER BY occurred_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new UserManagementErrorRowMapper(), limit);
    }
    
    /**
     * Delete old resolved errors for data retention
     */
    public void deleteOldResolvedErrors(LocalDateTime before) {
        String sql = "DELETE FROM user_management_errors WHERE is_resolved = true AND occurred_at < ?";
        jdbcTemplate.update(sql, before);
    }
    
    /**
     * Get recent authentication failure patterns
     */
    public List<Object[]> getAuthenticationFailurePatterns(LocalDateTime since) {
        String sql = """
            SELECT ip_address, username, COUNT(*) as failure_count, MAX(occurred_at) as last_failure
            FROM user_management_errors 
            WHERE error_type = 'AUTHENTICATION' AND occurred_at >= ? 
            GROUP BY ip_address, username 
            HAVING COUNT(*) > 3
            ORDER BY failure_count DESC
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString("ip_address"),
            rs.getString("username"),
            rs.getLong("failure_count"),
            rs.getTimestamp("last_failure").toLocalDateTime()
        }, since);
    }
    
    /**
     * Row mapper for UserManagementError entity
     */
    private static class UserManagementErrorRowMapper implements RowMapper<UserManagementError> {
        @Override
        public UserManagementError mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserManagementError error = new UserManagementError();
            
            error.setId(UUID.fromString(rs.getString("id")));
            error.setErrorType(UserManagementError.ErrorType.valueOf(rs.getString("error_type")));
            error.setErrorCode(rs.getString("error_code"));
            error.setAction(rs.getString("action"));
            error.setErrorMessage(rs.getString("error_message"));
            error.setUsername(rs.getString("username"));
            error.setIpAddress(rs.getString("ip_address"));
            error.setUserAgent(rs.getString("user_agent"));
            error.setThreatLevel(UserManagementError.ThreatLevel.valueOf(rs.getString("threat_level")));
            error.setOccurredAt(rs.getTimestamp("occurred_at").toLocalDateTime());
            error.setResolved(rs.getBoolean("is_resolved"));
            error.setResolutionNotes(rs.getString("resolution_notes"));
            
            String transactionLogIdStr = rs.getString("transaction_log_id");
            if (transactionLogIdStr != null) {
                error.setTransactionLogId(UUID.fromString(transactionLogIdStr));
            }
            
            return error;
        }
    }
}