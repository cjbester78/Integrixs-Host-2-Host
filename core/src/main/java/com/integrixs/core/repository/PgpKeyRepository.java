package com.integrixs.core.repository;

import com.integrixs.shared.model.PgpKey;
import com.integrixs.core.service.AuditService;
import com.integrixs.shared.util.AuditUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Repository for PGP key management operations
 */
@Repository
public class PgpKeyRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(PgpKeyRepository.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    
    @Autowired
    public PgpKeyRepository(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }
    
    /**
     * Find all PGP keys
     */
    public List<PgpKey> findAll() {
        try {
            String sql = """
                SELECT id, key_name, description, key_type, key_size, user_id, fingerprint, key_id,
                       public_key, private_key, algorithm, expires_at, revoked_at, revocation_reason,
                       can_encrypt, can_sign, can_certify, can_authenticate,
                       imported_from, exported_count, last_used_at,
                       created_at, updated_at, created_by, updated_by
                FROM pgp_keys 
                ORDER BY created_at DESC
                """;
            
            return jdbcTemplate.query(sql, pgpKeyRowMapper());
            
        } catch (Exception e) {
            logger.error("Error finding all PGP keys: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Find PGP key by ID
     */
    public Optional<PgpKey> findById(UUID id) {
        try {
            String sql = """
                SELECT id, key_name, description, key_type, key_size, user_id, fingerprint, key_id,
                       public_key, private_key, algorithm, expires_at, revoked_at, revocation_reason,
                       can_encrypt, can_sign, can_certify, can_authenticate,
                       imported_from, exported_count, last_used_at,
                       created_at, updated_at, created_by, updated_by
                FROM pgp_keys 
                WHERE id = ?
                """;
            
            List<PgpKey> results = jdbcTemplate.query(sql, pgpKeyRowMapper(), id);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
            
        } catch (Exception e) {
            logger.error("Error finding PGP key by ID '{}': {}", id, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Find PGP key by fingerprint
     */
    public Optional<PgpKey> findByFingerprint(String fingerprint) {
        try {
            String sql = """
                SELECT id, key_name, description, key_type, key_size, user_id, fingerprint, key_id,
                       public_key, private_key, algorithm, expires_at, revoked_at, revocation_reason,
                       can_encrypt, can_sign, can_certify, can_authenticate,
                       imported_from, exported_count, last_used_at,
                       created_at, updated_at, created_by, updated_by
                FROM pgp_keys 
                WHERE fingerprint = ?
                """;
            
            List<PgpKey> results = jdbcTemplate.query(sql, pgpKeyRowMapper(), fingerprint);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
            
        } catch (Exception e) {
            logger.error("Error finding PGP key by fingerprint '{}': {}", fingerprint, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Find PGP key by name
     */
    public Optional<PgpKey> findByKeyName(String keyName) {
        try {
            String sql = """
                SELECT id, key_name, description, key_type, key_size, user_id, fingerprint, key_id,
                       public_key, private_key, algorithm, expires_at, revoked_at, revocation_reason,
                       can_encrypt, can_sign, can_certify, can_authenticate,
                       imported_from, exported_count, last_used_at,
                       created_at, updated_at, created_by, updated_by
                FROM pgp_keys 
                WHERE key_name = ?
                """;
            
            List<PgpKey> results = jdbcTemplate.query(sql, pgpKeyRowMapper(), keyName);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
            
        } catch (Exception e) {
            logger.error("Error finding PGP key by name '{}': {}", keyName, e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Save PGP key (insert only - PGP keys should never be updated)
     */
    public PgpKey save(PgpKey pgpKey) {
        // PGP keys should always be new inserts - if there's an issue, generate a new key
        if (pgpKey.getId() != null) {
            throw new IllegalArgumentException("PGP keys cannot be updated. Generate a new key instead.");
        }
        pgpKey.setId(UUID.randomUUID());
        return insert(pgpKey);
    }
    
    /**
     * Insert new PGP key
     */
    private PgpKey insert(PgpKey pgpKey) {
        try {
            String sql = """
                INSERT INTO pgp_keys (
                    id, key_name, description, key_type, key_size, user_id, fingerprint, key_id,
                    public_key, private_key, algorithm, expires_at, revoked_at, revocation_reason,
                    can_encrypt, can_sign, can_certify, can_authenticate,
                    imported_from, exported_count, last_used_at, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            pgpKey.setCreatedAt(LocalDateTime.now());
            // Don't set updated_at for new records
            
            // Get current user ID from audit utils and convert to UUID
            String createdByString = AuditUtils.getCurrentUserId();
            UUID createdByUuid;
            try {
                createdByUuid = UUID.fromString(createdByString);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid UUID format from audit utils: {}", createdByString);
                createdByUuid = null; // Allow null for system operations
            }
            
            jdbcTemplate.update(sql,
                pgpKey.getId(),
                pgpKey.getKeyName(),
                pgpKey.getDescription(),
                pgpKey.getKeyType().name(),
                pgpKey.getKeySize(),
                pgpKey.getUserId(),
                pgpKey.getFingerprint(),
                pgpKey.getKeyId(),
                pgpKey.getPublicKey(),
                pgpKey.getPrivateKey(),
                pgpKey.getAlgorithm(),
                pgpKey.getExpiresAt(),
                pgpKey.getRevokedAt(),
                pgpKey.getRevocationReason(),
                pgpKey.getCanEncrypt(),
                pgpKey.getCanSign(),
                pgpKey.getCanCertify(),
                pgpKey.getCanAuthenticate(),
                pgpKey.getImportedFrom(),
                pgpKey.getExportedCount(),
                pgpKey.getLastUsedAt(),
                pgpKey.getCreatedAt(),
                createdByUuid
            );
            
            // Audit log
            auditService.logDatabaseOperation("INSERT", "pgp_keys", pgpKey.getId(), 
                "Created PGP key: " + pgpKey.getKeyName(), true, null);
            
            logger.info("Inserted new PGP key: {}", pgpKey.getKeyName());
            return pgpKey;
            
        } catch (Exception e) {
            logger.error("Error inserting PGP key '{}': {}", pgpKey.getKeyName(), e.getMessage(), e);
            throw new RuntimeException("Failed to save PGP key: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update existing PGP key
     */
    private PgpKey update(PgpKey pgpKey) {
        try {
            String sql = """
                UPDATE pgp_keys 
                SET key_name = ?, description = ?, key_type = ?, key_size = ?, user_id = ?,
                    fingerprint = ?, key_id = ?, public_key = ?, private_key = ?, algorithm = ?,
                    expires_at = ?, revoked_at = ?, revocation_reason = ?,
                    can_encrypt = ?, can_sign = ?, can_certify = ?, can_authenticate = ?,
                    imported_from = ?, exported_count = ?, last_used_at = ?, 
                    updated_at = ?, updated_by = ?
                WHERE id = ?
                """;
            
            pgpKey.setUpdatedAt(LocalDateTime.now());
            String updatedBy = AuditUtils.getCurrentUserId();
            
            int updated = jdbcTemplate.update(sql,
                pgpKey.getKeyName(),
                pgpKey.getDescription(),
                pgpKey.getKeyType().name(),
                pgpKey.getKeySize(),
                pgpKey.getUserId(),
                pgpKey.getFingerprint(),
                pgpKey.getKeyId(),
                pgpKey.getPublicKey(),
                pgpKey.getPrivateKey(),
                pgpKey.getAlgorithm(),
                pgpKey.getExpiresAt(),
                pgpKey.getRevokedAt(),
                pgpKey.getRevocationReason(),
                pgpKey.getCanEncrypt(),
                pgpKey.getCanSign(),
                pgpKey.getCanCertify(),
                pgpKey.getCanAuthenticate(),
                pgpKey.getImportedFrom(),
                pgpKey.getExportedCount(),
                pgpKey.getLastUsedAt(),
                pgpKey.getUpdatedAt(),
                updatedBy,
                pgpKey.getId()
            );
            
            if (updated > 0) {
                // Audit log
                auditService.logDatabaseOperation("UPDATE", "pgp_keys", pgpKey.getId(), 
                    "Updated PGP key: " + pgpKey.getKeyName(), true, null);
                
                logger.info("Updated PGP key: {}", pgpKey.getKeyName());
                return pgpKey;
            } else {
                throw new RuntimeException("PGP key not found for update: " + pgpKey.getId());
            }
            
        } catch (Exception e) {
            logger.error("Error updating PGP key '{}': {}", pgpKey.getKeyName(), e.getMessage());
            throw new RuntimeException("Failed to update PGP key", e);
        }
    }
    
    /**
     * Delete PGP key by ID
     */
    public boolean delete(UUID id) {
        try {
            // Get key info for audit log
            Optional<PgpKey> pgpKey = findById(id);
            
            String sql = "DELETE FROM pgp_keys WHERE id = ?";
            int deleted = jdbcTemplate.update(sql, id);
            
            if (deleted > 0) {
                // Audit log
                String keyName = pgpKey.map(PgpKey::getKeyName).orElse("Unknown");
                auditService.logDatabaseOperation("DELETE", "pgp_keys", id, 
                    "Deleted PGP key: " + keyName, true, null);
                
                logger.info("Deleted PGP key with ID: {}", id);
                return true;
            } else {
                logger.warn("No PGP key found with ID: {}", id);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error deleting PGP key with ID '{}': {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete PGP key", e);
        }
    }
    
    /**
     * Update export count
     */
    public void updateExportCount(UUID id) {
        try {
            String sql = "UPDATE pgp_keys SET exported_count = exported_count + 1, updated_at = ?, updated_by = ? WHERE id = ?";
            UUID updatedBy = getUpdatedByUuid();
            jdbcTemplate.update(sql, LocalDateTime.now(), updatedBy, id);

        } catch (Exception e) {
            logger.error("Error updating export count for PGP key '{}': {}", id, e.getMessage());
        }
    }

    /**
     * Update last used timestamp
     */
    public void updateLastUsed(UUID id) {
        try {
            String sql = "UPDATE pgp_keys SET last_used_at = ?, updated_at = ?, updated_by = ? WHERE id = ?";
            LocalDateTime now = LocalDateTime.now();
            UUID updatedBy = getUpdatedByUuid();
            jdbcTemplate.update(sql, now, now, updatedBy, id);

        } catch (Exception e) {
            logger.error("Error updating last used timestamp for PGP key '{}': {}", id, e.getMessage());
        }
    }
    
    /**
     * Revoke PGP key
     */
    public void revoke(UUID id, String reason, UUID revokedBy) {
        try {
            String sql = """
                UPDATE pgp_keys 
                SET revoked_at = ?, revocation_reason = ?, updated_at = ?, updated_by = ?
                WHERE id = ?
                """;
            
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(sql, now, reason, now, revokedBy, id);
            
            // Audit log
            auditService.logDatabaseOperation("UPDATE", "pgp_keys", id, 
                "Revoked PGP key. Reason: " + reason, true, null);
            
            logger.info("Revoked PGP key with ID: {} Reason: {}", id, reason);
            
        } catch (Exception e) {
            logger.error("Error revoking PGP key '{}': {}", id, e.getMessage());
            throw new RuntimeException("Failed to revoke PGP key", e);
        }
    }
    
    /**
     * Find expired keys
     */
    public List<PgpKey> findExpiredKeys() {
        try {
            String sql = """
                SELECT id, key_name, description, key_type, key_size, user_id, fingerprint, key_id,
                       public_key, private_key, algorithm, expires_at, revoked_at, revocation_reason,
                       can_encrypt, can_sign, can_certify, can_authenticate,
                       imported_from, exported_count, last_used_at,
                       created_at, updated_at, created_by, updated_by
                FROM pgp_keys 
                WHERE expires_at IS NOT NULL AND expires_at < ?
                ORDER BY expires_at DESC
                """;
            
            return jdbcTemplate.query(sql, pgpKeyRowMapper(), LocalDateTime.now());
            
        } catch (Exception e) {
            logger.error("Error finding expired PGP keys: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Row mapper for PGP key
     */
    private RowMapper<PgpKey> pgpKeyRowMapper() {
        return new RowMapper<PgpKey>() {
            @Override
            public PgpKey mapRow(ResultSet rs, int rowNum) throws SQLException {
                PgpKey pgpKey = new PgpKey();
                
                pgpKey.setId(UUID.fromString(rs.getString("id")));
                pgpKey.setKeyName(rs.getString("key_name"));
                pgpKey.setDescription(rs.getString("description"));
                pgpKey.setKeyType(PgpKey.KeyType.valueOf(rs.getString("key_type")));
                pgpKey.setKeySize(rs.getInt("key_size"));
                pgpKey.setUserId(rs.getString("user_id"));
                pgpKey.setFingerprint(rs.getString("fingerprint"));
                pgpKey.setKeyId(rs.getString("key_id"));
                
                pgpKey.setPublicKey(rs.getString("public_key"));
                pgpKey.setPrivateKey(rs.getString("private_key"));
                pgpKey.setAlgorithm(rs.getString("algorithm"));
                
                // Handle timestamps
                if (rs.getTimestamp("expires_at") != null) {
                    pgpKey.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                }
                if (rs.getTimestamp("revoked_at") != null) {
                    pgpKey.setRevokedAt(rs.getTimestamp("revoked_at").toLocalDateTime());
                }
                pgpKey.setRevocationReason(rs.getString("revocation_reason"));
                
                // Usage flags
                pgpKey.setCanEncrypt(rs.getBoolean("can_encrypt"));
                pgpKey.setCanSign(rs.getBoolean("can_sign"));
                pgpKey.setCanCertify(rs.getBoolean("can_certify"));
                pgpKey.setCanAuthenticate(rs.getBoolean("can_authenticate"));
                
                // Metadata
                pgpKey.setImportedFrom(rs.getString("imported_from"));
                pgpKey.setExportedCount(rs.getInt("exported_count"));
                if (rs.getTimestamp("last_used_at") != null) {
                    pgpKey.setLastUsedAt(rs.getTimestamp("last_used_at").toLocalDateTime());
                }
                
                // Audit fields
                if (rs.getTimestamp("created_at") != null) {
                    pgpKey.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
                if (rs.getTimestamp("updated_at") != null) {
                    pgpKey.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
                
                String createdByStr = rs.getString("created_by");
                if (createdByStr != null) {
                    pgpKey.setCreatedBy(UUID.fromString(createdByStr));
                }
                String updatedByStr = rs.getString("updated_by");
                if (updatedByStr != null) {
                    pgpKey.setUpdatedBy(UUID.fromString(updatedByStr));
                }

                return pgpKey;
            }
        };
    }

    /**
     * Get current user UUID for updated_by field
     */
    private UUID getUpdatedByUuid() {
        try {
            String userId = AuditUtils.getCurrentUserId();
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}