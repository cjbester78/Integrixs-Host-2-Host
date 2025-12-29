package com.integrixs.core.repository;

import com.integrixs.shared.model.SshKey;
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
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC Repository for SSH key management
 */
@Repository
public class SshKeyRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    
    @Autowired
    public SshKeyRepository(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }
    
    public List<SshKey> findAll() {
        String sql = """
            SELECT id, name, description, private_key, public_key, key_type, key_size, 
                   fingerprint, active, created_at, created_by, expires_at
            FROM ssh_keys 
            ORDER BY created_at DESC
        """;
        
        return jdbcTemplate.query(sql, new SshKeyRowMapper());
    }
    
    public List<SshKey> findAllActive() {
        String sql = """
            SELECT id, name, description, private_key, public_key, key_type, key_size, 
                   fingerprint, active, created_at, created_by, expires_at
            FROM ssh_keys 
            WHERE active = true 
            ORDER BY created_at DESC
        """;
        
        return jdbcTemplate.query(sql, new SshKeyRowMapper());
    }
    
    public Optional<SshKey> findById(UUID id) {
        String sql = """
            SELECT id, name, description, private_key, public_key, key_type, key_size, 
                   fingerprint, active, created_at, created_by, expires_at
            FROM ssh_keys 
            WHERE id = ?
        """;
        
        try {
            SshKey sshKey = jdbcTemplate.queryForObject(sql, new SshKeyRowMapper(), id);
            return Optional.ofNullable(sshKey);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    public Optional<SshKey> findByName(String name) {
        String sql = """
            SELECT id, name, description, private_key, public_key, key_type, key_size, 
                   fingerprint, active, created_at, created_by, expires_at
            FROM ssh_keys 
            WHERE name = ?
        """;
        
        try {
            SshKey sshKey = jdbcTemplate.queryForObject(sql, new SshKeyRowMapper(), name);
            return Optional.ofNullable(sshKey);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    public Optional<SshKey> findByFingerprint(String fingerprint) {
        String sql = """
            SELECT id, name, description, private_key, public_key, key_type, key_size, 
                   fingerprint, active, created_at, created_by, expires_at
            FROM ssh_keys 
            WHERE fingerprint = ?
        """;
        
        try {
            SshKey sshKey = jdbcTemplate.queryForObject(sql, new SshKeyRowMapper(), fingerprint);
            return Optional.ofNullable(sshKey);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    public List<SshKey> findByCreatedBy(UUID userId) {
        String sql = """
            SELECT id, name, description, private_key, public_key, key_type, key_size, 
                   fingerprint, active, created_at, created_by, expires_at
            FROM ssh_keys 
            WHERE created_by = ? 
            ORDER BY created_at DESC
        """;
        
        return jdbcTemplate.query(sql, new SshKeyRowMapper(), userId);
    }
    
    public UUID save(SshKey sshKey) {
        if (sshKey.getId() == null) {
            return insert(sshKey);
        } else {
            throw new IllegalStateException("SSH keys cannot be updated - only created. To modify a key, delete it and generate a new one.");
        }
    }
    
    private UUID insert(SshKey sshKey) {
        UUID id = UUID.randomUUID();
        String sql = """
            INSERT INTO ssh_keys (id, name, description, private_key, public_key, key_type, key_size, 
                                 fingerprint, active, created_at, created_by, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        LocalDateTime now = LocalDateTime.now();
        String createdBy = AuditUtils.getCurrentUserId();
        
        jdbcTemplate.update(sql,
                id,
                sshKey.getName(),
                sshKey.getDescription(),
                sshKey.getPrivateKey(),
                sshKey.getPublicKey(),
                sshKey.getKeyType(),
                sshKey.getKeySize(),
                sshKey.getFingerprint(),
                sshKey.getActive() != null ? sshKey.getActive() : true,
                now,
                createdBy,
                sshKey.getExpiresAt()
        );
        
        sshKey.setId(id);
        sshKey.setCreatedAt(now);
        
        // Log audit trail for SSH key creation (security critical)
        auditService.logDatabaseOperation("INSERT", "ssh_keys", id, 
            sshKey.getName() + " (" + sshKey.getKeyType() + ")", true, null);
        
        return id;
    }
    
    // SSH keys cannot be updated - only active/inactive or deleted and regenerated
    
    
    public void deleteById(UUID id) {
        // Get SSH key info before deletion for audit trail
        Optional<SshKey> sshKeyOpt = findById(id);
        String keyName = sshKeyOpt.map(key -> key.getName() + " (" + key.getKeyType() + ")").orElse("unknown SSH key");
        
        String sql = "DELETE FROM ssh_keys WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        // Log audit trail for SSH key deletion (security critical)
        auditService.logDatabaseOperation("DELETE", "ssh_keys", id, 
            keyName, rowsAffected > 0, rowsAffected == 0 ? "SSH key not found" : null);
    }
    
    public void delete(UUID id) {
        deleteById(id);
    }
    
    public void setActive(UUID id, boolean active) {
        // Get SSH key info for audit trail
        Optional<SshKey> sshKeyOpt = findById(id);
        String keyName = sshKeyOpt.map(key -> key.getName() + " (" + key.getKeyType() + ")").orElse("unknown SSH key");
        
        String sql = "UPDATE ssh_keys SET active = ? WHERE id = ?";
        jdbcTemplate.update(sql, active, id);
        
        // Log audit trail for SSH key active/inactive (security critical)
        auditService.logDatabaseOperation("UPDATE", "ssh_keys", id, 
            keyName + " " + (active ? "active" : "inactive"), true, null);
    }
    
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM ssh_keys WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }
    
    public boolean existsByFingerprint(String fingerprint) {
        String sql = "SELECT COUNT(*) FROM ssh_keys WHERE fingerprint = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fingerprint);
        return count != null && count > 0;
    }
    
    /**
     * Row mapper for SSH key results
     */
    private static class SshKeyRowMapper implements RowMapper<SshKey> {
        
        @Override
        public SshKey mapRow(ResultSet rs, int rowNum) throws SQLException {
            SshKey sshKey = new SshKey();
            
            sshKey.setId(UUID.fromString(rs.getString("id")));
            sshKey.setName(rs.getString("name"));
            sshKey.setDescription(rs.getString("description"));
            sshKey.setPrivateKey(rs.getString("private_key"));
            sshKey.setPublicKey(rs.getString("public_key"));
            sshKey.setKeyType(rs.getString("key_type"));
            sshKey.setKeySize(rs.getInt("key_size"));
            sshKey.setFingerprint(rs.getString("fingerprint"));
            sshKey.setActive(rs.getBoolean("active"));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                sshKey.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            String createdByStr = rs.getString("created_by");
            if (createdByStr != null) {
                sshKey.setCreatedBy(UUID.fromString(createdByStr));
            }
            
            
            
            Timestamp expiresAt = rs.getTimestamp("expires_at");
            if (expiresAt != null) {
                sshKey.setExpiresAt(expiresAt.toLocalDateTime());
            }
            
            return sshKey;
        }
    }
}