package com.integrixs.core.repository;

import com.integrixs.shared.model.UserSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserSession entity using native SQL with Spring JDBC
 * Provides optimized queries for user session tracking and security monitoring
 */
@Repository
public class UserSessionRepository {
    
    private final JdbcTemplate jdbcTemplate;
    
    public UserSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Save a user session to the database
     */
    public UserSession save(UserSession session) {
        // Initialize timestamps if not set
        session.initializeTimestamps();
        
        // Generate UUID if not set
        if (session.getId() == null) {
            session.setId(UUID.randomUUID());
        }
        
        String sql = """
            INSERT INTO user_sessions (
                id, user_id, session_id, ip_address, user_agent, 
                created_at, expires_at, is_active, login_log_id
            ) VALUES (?, ?, ?, ?::inet, ?, ?, ?, ?, ?)
            """;
        
        jdbcTemplate.update(sql,
            session.getId(),
            session.getUserId(),
            session.getSessionId(),
            session.getIpAddress(),
            session.getUserAgent(),
            session.getCreatedAt(),
            session.getExpiresAt(),
            session.isActive(),
            session.getLoginLogId()
        );
        
        return session;
    }
    
    /**
     * Update an existing user session
     */
    public void update(UserSession session) {
        String sql = """
            UPDATE user_sessions SET 
                is_active = ?, 
                last_accessed_at = NOW()
            WHERE id = ?
            """;
        
        jdbcTemplate.update(sql, session.isActive(), session.getId());
    }
    
    /**
     * Find session by session ID
     */
    public Optional<UserSession> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM user_sessions WHERE session_id = ? AND is_active = true";
        List<UserSession> sessions = jdbcTemplate.query(sql, new UserSessionRowMapper(), sessionId);
        return sessions.isEmpty() ? Optional.empty() : Optional.of(sessions.get(0));
    }
    
    /**
     * Find all active sessions for a user
     */
    public List<UserSession> findActiveSessionsByUserId(UUID userId) {
        String sql = "SELECT * FROM user_sessions WHERE user_id = ? AND is_active = true AND expires_at > NOW() ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new UserSessionRowMapper(), userId);
    }
    
    /**
     * Find sessions by user ID with limit, ordered by creation time descending
     */
    public List<UserSession> findByUserIdOrderByCreatedAtDesc(UUID userId, int limit) {
        String sql = "SELECT * FROM user_sessions WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, new UserSessionRowMapper(), userId, limit);
    }
    
    /**
     * Find sessions by IP address within a time range
     */
    public List<UserSession> findByIpAddressSince(String ipAddress, LocalDateTime since) {
        String sql = "SELECT * FROM user_sessions WHERE ip_address = ?::inet AND created_at >= ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, new UserSessionRowMapper(), ipAddress, since);
    }
    
    /**
     * Count active sessions for a user
     */
    public long countActiveSessionsByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) FROM user_sessions WHERE user_id = ? AND is_active = true AND expires_at > NOW()";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return count != null ? count : 0;
    }
    
    /**
     * Deactivate session by session ID
     */
    public void deactivateSession(String sessionId) {
        String sql = "UPDATE user_sessions SET is_active = false WHERE session_id = ?";
        jdbcTemplate.update(sql, sessionId);
    }
    
    /**
     * Deactivate all sessions for a user
     */
    public void deactivateAllUserSessions(UUID userId) {
        String sql = "UPDATE user_sessions SET is_active = false WHERE user_id = ?";
        jdbcTemplate.update(sql, userId);
    }
    
    /**
     * Find expired sessions
     */
    public List<UserSession> findExpiredSessions() {
        String sql = "SELECT * FROM user_sessions WHERE is_active = true AND expires_at <= NOW()";
        return jdbcTemplate.query(sql, new UserSessionRowMapper());
    }
    
    /**
     * Clean up expired sessions
     */
    public void cleanupExpiredSessions() {
        String sql = "UPDATE user_sessions SET is_active = false WHERE is_active = true AND expires_at <= NOW()";
        jdbcTemplate.update(sql);
    }
    
    /**
     * Delete old inactive sessions
     */
    public void deleteOldInactiveSessions(LocalDateTime before) {
        String sql = "DELETE FROM user_sessions WHERE is_active = false AND created_at < ?";
        jdbcTemplate.update(sql, before);
    }
    
    /**
     * Get session statistics for dashboard
     */
    public List<Object[]> getSessionStatistics(LocalDateTime since) {
        String sql = """
            SELECT 
                CASE WHEN is_active = true AND expires_at > NOW() THEN 'ACTIVE' 
                     WHEN expires_at <= NOW() THEN 'EXPIRED' 
                     ELSE 'INACTIVE' END as status,
                COUNT(*) as count
            FROM user_sessions 
            WHERE created_at >= ? 
            GROUP BY 
                CASE WHEN is_active = true AND expires_at > NOW() THEN 'ACTIVE' 
                     WHEN expires_at <= NOW() THEN 'EXPIRED' 
                     ELSE 'INACTIVE' END
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Object[]{
            rs.getString("status"),
            rs.getLong("count")
        }, since);
    }
    
    /**
     * Row mapper for UserSession entity
     */
    private static class UserSessionRowMapper implements RowMapper<UserSession> {
        @Override
        public UserSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserSession session = new UserSession();
            
            session.setId(UUID.fromString(rs.getString("id")));
            session.setUserId(UUID.fromString(rs.getString("user_id")));
            session.setSessionId(rs.getString("session_id"));
            session.setIpAddress(rs.getString("ip_address"));
            session.setUserAgent(rs.getString("user_agent"));
            session.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            session.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
            session.setActive(rs.getBoolean("is_active"));
            
            String loginLogIdStr = rs.getString("login_log_id");
            if (loginLogIdStr != null) {
                session.setLoginLogId(UUID.fromString(loginLogIdStr));
            }
            
            return session;
        }
    }
}