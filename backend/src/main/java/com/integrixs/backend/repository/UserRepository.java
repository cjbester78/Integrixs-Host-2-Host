package com.integrixs.backend.repository;

import com.integrixs.backend.model.User;
import com.integrixs.shared.service.SystemAuditService;
import com.integrixs.shared.util.AuditUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
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
 * JDBC-based repository for User entities
 * Replaces the in-memory storage with database persistence
 */
@Repository
public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final SystemAuditService auditService;

    public UserRepository(JdbcTemplate jdbcTemplate, SystemAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    /**
     * User row mapper for converting ResultSet to User objects
     */
    private final RowMapper<User> userRowMapper = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(UUID.fromString(rs.getString("id")));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setPassword(rs.getString("password_hash"));
            user.setFullName(rs.getString("full_name"));
            user.setRole(User.UserRole.valueOf(rs.getString("role")));
            user.setTimezone(rs.getString("timezone"));
            user.setEnabled(rs.getBoolean("enabled"));
            user.setAccountNonExpired(rs.getBoolean("account_non_expired"));
            user.setAccountNonLocked(rs.getBoolean("account_non_locked"));
            user.setCredentialsNonExpired(rs.getBoolean("credentials_non_expired"));
            user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
            
            // Handle nullable timestamps
            if (rs.getTimestamp("last_login") != null) {
                user.setLastLogin(rs.getTimestamp("last_login").toLocalDateTime());
            }
            if (rs.getTimestamp("created_at") != null) {
                user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            if (rs.getTimestamp("updated_at") != null) {
                user.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            }
            
            return user;
        }
    };

    /**
     * Save or update a user
     */
    public User save(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID());
            return insert(user);
        } else {
            // Check if user actually exists in database
            Optional<User> existingUser = findById(user.getId());
            if (existingUser.isPresent()) {
                return update(user);
            } else {
                // User has ID but doesn't exist in DB, so insert
                return insert(user);
            }
        }
    }

    /**
     * Insert a new user
     */
    private User insert(User user) {
        String sql = """
            INSERT INTO users (
                id, username, email, password_hash, full_name, role, timezone,
                enabled, account_non_expired, account_non_locked, credentials_non_expired,
                failed_login_attempts, last_login, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        // Don't set updated_at for new records

        // Get current user ID for created_by field
        String createdBy = AuditUtils.getCurrentUserId();

        int rows = jdbcTemplate.update(sql,
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            user.getFullName(),
            user.getRole().name(),
            user.getTimezone(),
            user.isEnabled(),
            user.isAccountNonExpired(),
            user.isAccountNonLocked(),
            user.isCredentialsNonExpired(),
            user.getFailedLoginAttempts(),
            user.getLastLogin(),
            user.getCreatedAt(),
            createdBy
        );

        if (rows > 0) {
            logger.debug("Inserted user: {}", user.getUsername());
            
            // Log audit trail for user creation
            auditService.logDatabaseOperation("INSERT", "users", user.getId(), 
                user.getUsername(), true, null);
            
            return user;
        } else {
            String errorMessage = "Failed to insert user: " + user.getUsername();
            
            // Log failed audit trail
            auditService.logDatabaseOperation("INSERT", "users", user.getId(), 
                user.getUsername(), false, errorMessage);
                
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * Update an existing user
     */
    private User update(User user) {
        String sql = """
            UPDATE users SET 
                username = ?, email = ?, password_hash = ?, full_name = ?, role = ?, 
                timezone = ?, enabled = ?, account_non_expired = ?, account_non_locked = ?, 
                credentials_non_expired = ?, failed_login_attempts = ?, last_login = ?,
                updated_at = ?, updated_by = ?
            WHERE id = ?
            """;

        user.setUpdatedAt(LocalDateTime.now());

        // Get current user ID for updated_by field
        String updatedBy = AuditUtils.getCurrentUserId();

        int rows = jdbcTemplate.update(sql,
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            user.getFullName(),
            user.getRole().name(),
            user.getTimezone(),
            user.isEnabled(),
            user.isAccountNonExpired(),
            user.isAccountNonLocked(),
            user.isCredentialsNonExpired(),
            user.getFailedLoginAttempts(),
            user.getLastLogin(),
            user.getUpdatedAt(),
            updatedBy,
            user.getId()
        );

        if (rows > 0) {
            logger.debug("Updated user: {}", user.getUsername());
            
            // Log audit trail for user update
            auditService.logDatabaseOperation("UPDATE", "users", user.getId(), 
                user.getUsername(), true, null);
            
            return user;
        } else {
            String errorMessage = "Failed to update user: " + user.getUsername();
            
            // Log failed audit trail
            auditService.logDatabaseOperation("UPDATE", "users", user.getId(), 
                user.getUsername(), false, errorMessage);
                
            throw new RuntimeException(errorMessage);
        }
    }

    /**
     * Find user by ID
     */
    public Optional<User> findById(UUID id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Find user by username
     */
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, username);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Find user by email
     */
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, email);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Find user by username or email
     */
    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) {
        String sql = "SELECT * FROM users WHERE username = ? OR email = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, usernameOrEmail, usernameOrEmail);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Check if username exists
     */
    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    /**
     * Check if email exists
     */
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
        return count != null && count > 0;
    }

    /**
     * Find all users
     */
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, userRowMapper);
    }

    /**
     * Find users by role
     */
    public List<User> findByRole(User.UserRole role) {
        String sql = "SELECT * FROM users WHERE role = ? ORDER BY created_at";
        return jdbcTemplate.query(sql, userRowMapper, role.name());
    }

    /**
     * Find active users
     */
    public List<User> findActiveUsers() {
        String sql = "SELECT * FROM users WHERE enabled = true AND account_non_locked = true ORDER BY username";
        return jdbcTemplate.query(sql, userRowMapper);
    }

    /**
     * Find active administrators
     */
    public List<User> findActiveAdministrators() {
        String sql = "SELECT * FROM users WHERE role = 'ADMINISTRATOR' AND enabled = true ORDER BY username";
        return jdbcTemplate.query(sql, userRowMapper);
    }

    /**
     * Find active viewers
     */
    public List<User> findActiveViewers() {
        String sql = "SELECT * FROM users WHERE role = 'VIEWER' AND enabled = true ORDER BY username";
        return jdbcTemplate.query(sql, userRowMapper);
    }

    /**
     * Delete user by ID
     */
    public void deleteById(UUID id) {
        // Get user info before deletion for audit trail
        Optional<User> userOpt = findById(id);
        String username = userOpt.map(User::getUsername).orElse("unknown");
        
        String sql = "DELETE FROM users WHERE id = ?";
        int rows = jdbcTemplate.update(sql, id);
        
        if (rows > 0) {
            logger.info("Deleted user with ID: {}", id);
            
            // Log audit trail for user deletion
            auditService.logDatabaseOperation("DELETE", "users", id, username, true, null);
        } else {
            String errorMessage = "Failed to delete user with ID: " + id;
            
            // Log failed audit trail
            auditService.logDatabaseOperation("DELETE", "users", id, username, false, errorMessage);
        }
    }

    /**
     * Update last login timestamp
     */
    public void updateLastLogin(UUID userId) {
        String sql = "UPDATE users SET last_login = ?, updated_at = ?, updated_by = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        String updatedBy = AuditUtils.getCurrentUserId();
        int rows = jdbcTemplate.update(sql, now, now, updatedBy, userId);
        if (rows > 0) {
            logger.debug("Updated last login for user ID: {}", userId);
        }
    }

    /**
     * Lock user account
     */
    public void lockAccount(UUID userId) {
        // Get user info for audit trail
        Optional<User> userOpt = findById(userId);
        String username = userOpt.map(User::getUsername).orElse("unknown");
        
        String sql = "UPDATE users SET account_non_locked = false, updated_at = ?, updated_by = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        String updatedBy = AuditUtils.getCurrentUserId();
        int rows = jdbcTemplate.update(sql, now, updatedBy, userId);
        
        if (rows > 0) {
            logger.info("Locked account for user ID: {}", userId);
            
            // Log audit trail for account lock (security critical)
            auditService.logDatabaseOperation("UPDATE", "users", userId, 
                username + " (ACCOUNT_LOCKED)", true, null);
        }
    }

    /**
     * Unlock user account
     */
    public void unlockAccount(UUID userId) {
        // Get user info for audit trail
        Optional<User> userOpt = findById(userId);
        String username = userOpt.map(User::getUsername).orElse("unknown");
        
        String sql = "UPDATE users SET account_non_locked = true, failed_login_attempts = 0, updated_at = ?, updated_by = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        String updatedBy = AuditUtils.getCurrentUserId();
        int rows = jdbcTemplate.update(sql, now, updatedBy, userId);
        
        if (rows > 0) {
            logger.info("Unlocked account for user ID: {}", userId);
            
            // Log audit trail for account unlock (security critical)
            auditService.logDatabaseOperation("UPDATE", "users", userId, 
                username + " (ACCOUNT_UNLOCKED)", true, null);
        }
    }

    /**
     * Change user password
     */
    public void changePassword(UUID userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ?, updated_at = ?, updated_by = ? WHERE id = ?";
        LocalDateTime now = LocalDateTime.now();
        String updatedBy = AuditUtils.getCurrentUserId();
        int rows = jdbcTemplate.update(sql, newPasswordHash, now, updatedBy, userId);
        if (rows > 0) {
            logger.info("Changed password for user ID: {}", userId);
        }
    }

    /**
     * Get user statistics
     */
    public UserStatistics getUserStatistics() {
        String totalSql = "SELECT COUNT(*) FROM users";
        String enabledSql = "SELECT COUNT(*) FROM users WHERE enabled = true";
        String adminsSql = "SELECT COUNT(*) FROM users WHERE role = 'ADMINISTRATOR'";
        String viewersSql = "SELECT COUNT(*) FROM users WHERE role = 'VIEWER'";
        String lockedSql = "SELECT COUNT(*) FROM users WHERE account_non_locked = false";

        Long total = jdbcTemplate.queryForObject(totalSql, Long.class);
        Long enabled = jdbcTemplate.queryForObject(enabledSql, Long.class);
        Long admins = jdbcTemplate.queryForObject(adminsSql, Long.class);
        Long viewers = jdbcTemplate.queryForObject(viewersSql, Long.class);
        Long locked = jdbcTemplate.queryForObject(lockedSql, Long.class);

        return new UserStatistics(
            total != null ? total : 0L,
            enabled != null ? enabled : 0L,
            admins != null ? admins : 0L,
            viewers != null ? viewers : 0L,
            locked != null ? locked : 0L
        );
    }

    /**
     * Search users by username, email, or full name
     */
    public List<User> searchUsers(String searchTerm) {
        String sql = """
            SELECT * FROM users 
            WHERE LOWER(username) LIKE LOWER(?) 
               OR LOWER(email) LIKE LOWER(?) 
               OR LOWER(full_name) LIKE LOWER(?)
            ORDER BY username
            """;
        String searchPattern = "%" + searchTerm + "%";
        return jdbcTemplate.query(sql, userRowMapper, searchPattern, searchPattern, searchPattern);
    }

    /**
     * Count users by role
     */
    public long countByRole(User.UserRole role) {
        String sql = "SELECT COUNT(*) FROM users WHERE role = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, role.name());
        return count != null ? count : 0L;
    }

    /**
     * Find users created after date
     */
    public List<User> findUsersCreatedAfter(LocalDateTime date) {
        String sql = "SELECT * FROM users WHERE created_at > ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, userRowMapper, date);
    }

    /**
     * Find users who never logged in
     */
    public List<User> findUsersWhoNeverLoggedIn() {
        String sql = "SELECT * FROM users WHERE last_login IS NULL ORDER BY created_at";
        return jdbcTemplate.query(sql, userRowMapper);
    }


    /**
     * User statistics record
     */
    public record UserStatistics(
        long totalUsers,
        long enabledUsers, 
        long administrators,
        long viewers,
        long lockedUsers
    ) {}
}