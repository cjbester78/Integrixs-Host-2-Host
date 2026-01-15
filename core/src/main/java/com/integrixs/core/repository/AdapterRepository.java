package com.integrixs.core.repository;

import com.integrixs.shared.model.Adapter;
import com.integrixs.core.service.AuditService;
import com.integrixs.shared.util.AuditUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC Repository for adapter management
 * Handles CRUD operations for reusable adapter components
 */
@Repository
public class AdapterRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public AdapterRepository(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Find all adapters
     */
    public List<Adapter> findAll() {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            ORDER BY created_at DESC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper());
    }
    
    /**
     * Find adapters by type
     */
    public List<Adapter> findByType(String adapterType) {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE adapter_type = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), adapterType);
    }
    
    /**
     * Find adapters by type and direction
     */
    public List<Adapter> findByTypeAndDirection(String adapterType, String direction) {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE adapter_type = ? AND direction = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), adapterType, direction);
    }
    
    /**
     * Find active adapters
     */
    public List<Adapter> findAllActive() {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE active = true
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper());
    }
    
    /**
     * Find adapter by ID
     */
    public Optional<Adapter> findById(UUID id) {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE id = ?
        """;
        
        try {
            Adapter adapter = jdbcTemplate.queryForObject(sql, new AdapterRowMapper(), id);
            return Optional.of(adapter);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find adapter by name
     */
    public Optional<Adapter> findByName(String name) {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE name = ?
        """;
        
        try {
            Adapter adapter = jdbcTemplate.queryForObject(sql, new AdapterRowMapper(), name);
            return Optional.of(adapter);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if adapter exists by name
     */
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM package_adapters WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }
    
    /**
     * Check if adapter exists by name excluding specific ID
     */
    public boolean existsByNameAndNotId(String name, UUID id) {
        String sql = "SELECT COUNT(*) FROM package_adapters WHERE name = ? AND id != ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name, id);
        return count != null && count > 0;
    }

    /**
     * Check if adapter has been imported by original adapter ID
     */
    public boolean existsByOriginalAdapterId(UUID originalAdapterId) {
        String sql = "SELECT COUNT(*) FROM package_adapters WHERE original_adapter_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, originalAdapterId);
        return count != null && count > 0;
    }

    /**
     * Find adapter by original adapter ID (for re-import/update scenarios)
     */
    public Optional<Adapter> findByOriginalAdapterId(UUID originalAdapterId) {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id, original_adapter_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE original_adapter_id = ?
        """;

        try {
            Adapter adapter = jdbcTemplate.queryForObject(sql, new AdapterRowMapper(), originalAdapterId);
            return Optional.of(adapter);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Save adapter (insert)
     */
    public UUID save(Adapter adapter) {
        if (adapter.getId() == null) {
            adapter.setId(UUID.randomUUID());
        }
        
        // Set audit fields for INSERT
        LocalDateTime now = LocalDateTime.now();
        adapter.setCreatedAt(now);
        // Don't set updated_at for new records
        
        String createdByStr = AuditUtils.getCurrentUserId();
        UUID createdBy;
        try {
            createdBy = UUID.fromString(createdByStr);
        } catch (IllegalArgumentException e) {
            // If still not a valid UUID, use the createdBy from the adapter object if available
            if (adapter.getCreatedBy() != null) {
                createdBy = adapter.getCreatedBy();
            } else {
                throw new RuntimeException("Unable to determine valid user ID for audit trail: " + createdByStr);
            }
        }
        
        String sql = """
            INSERT INTO package_adapters (
                id, name, description, adapter_type, direction, configuration,
                connection_validated, last_test_at, test_result, active, status,
                average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.update(sql,
            adapter.getId(),
            adapter.getName(),
            adapter.getDescription(),
            adapter.getAdapterType(),
            adapter.getDirection(),
            convertMapToJson(adapter.getConfiguration()),
            adapter.getConnectionValidated(),
            adapter.getLastTestAt(),
            adapter.getTestResult(),
            adapter.getActive(),
            adapter.getStatus() != null ? adapter.getStatus().name() : "STOPPED",
            adapter.getAverageExecutionTimeMs(),
            adapter.getSuccessRatePercent(),
            adapter.getPackageId(),
            adapter.getDeployedFromPackageId(),
            adapter.getCreatedAt(),
            createdBy
        );
        
        // Log audit trail for adapter creation
        auditService.logDatabaseOperation("INSERT", "adapters", adapter.getId(), 
            adapter.getName(), true, null);
        
        return adapter.getId();
    }
    
    /**
     * Update adapter
     */
    public void update(Adapter adapter) {
        adapter.setUpdatedAt(LocalDateTime.now());
        String updatedByStr = AuditUtils.getCurrentUserId();
        UUID updatedBy;
        try {
            updatedBy = UUID.fromString(updatedByStr);
        } catch (IllegalArgumentException e) {
            // If still not a valid UUID, use the updatedBy from the adapter object if available
            if (adapter.getUpdatedBy() != null) {
                updatedBy = adapter.getUpdatedBy();
            } else {
                throw new RuntimeException("Unable to determine valid user ID for audit trail: " + updatedByStr);
            }
        }
        
        String sql = """
            UPDATE package_adapters SET
                name = ?, description = ?, adapter_type = ?, direction = ?,
                configuration = ?::jsonb, connection_validated = ?, last_test_at = ?,
                test_result = ?, active = ?, status = ?, average_execution_time_ms = ?,
                success_rate_percent = ?, package_id = ?, deployed_from_package_id = ?,
                updated_at = ?, updated_by = ?
            WHERE id = ?
        """;

        jdbcTemplate.update(sql,
            adapter.getName(),
            adapter.getDescription(),
            adapter.getAdapterType(),
            adapter.getDirection(),
            convertMapToJson(adapter.getConfiguration()),
            adapter.getConnectionValidated(),
            adapter.getLastTestAt(),
            adapter.getTestResult(),
            adapter.getActive(),
            adapter.getStatus() != null ? adapter.getStatus().name() : "STOPPED",
            adapter.getAverageExecutionTimeMs(),
            adapter.getSuccessRatePercent(),
            adapter.getPackageId(),
            adapter.getDeployedFromPackageId(),
            adapter.getUpdatedAt(),
            updatedBy,
            adapter.getId()
        );
        
        // Log audit trail for adapter update
        auditService.logDatabaseOperation("UPDATE", "adapters", adapter.getId(), 
            adapter.getName(), true, null);
    }
    
    /**
     * Update adapter test result
     */
    public void updateTestResult(UUID id, boolean successful, String result) {
        String sql = """
            UPDATE package_adapters SET
                connection_validated = ?,
                test_result = ?,
                last_test_at = ?,
                updated_at = ?,
                updated_by = ?
            WHERE id = ?
        """;

        LocalDateTime now = LocalDateTime.now();
        UUID updatedBy = getUpdatedByUuid();
        jdbcTemplate.update(sql, successful, result, now, now, updatedBy, id);

        // Log audit trail for adapter test result update
        auditService.logDatabaseOperation("UPDATE", "adapters", id,
            "adapter test result", true, null);
    }
    
    /**
     * Update performance metrics
     */
    public void updatePerformanceMetrics(UUID id, long executionTimeMs, BigDecimal successRate) {
        String sql = """
            UPDATE package_adapters SET
                average_execution_time_ms = ?,
                success_rate_percent = ?,
                updated_at = ?,
                updated_by = ?
            WHERE id = ?
        """;

        UUID updatedBy = getUpdatedByUuid();
        jdbcTemplate.update(sql, executionTimeMs, successRate, LocalDateTime.now(), updatedBy, id);

        // Log audit trail for adapter performance metrics update
        auditService.logDatabaseOperation("UPDATE", "adapters", id,
            "adapter performance metrics", true, null);
    }
    
    /**
     * Set adapter enabled/disabled
     */
    public void setActive(UUID id, boolean active) {
        String sql = """
            UPDATE package_adapters SET
                active = ?,
                updated_at = ?,
                updated_by = ?
            WHERE id = ?
        """;

        UUID updatedBy = getUpdatedByUuid();
        jdbcTemplate.update(sql, active, LocalDateTime.now(), updatedBy, id);

        // Log audit trail for adapter enabled/disabled status update
        auditService.logDatabaseOperation("UPDATE", "adapters", id,
            "adapter " + (active ? "active" : "inactive"), true, null);
    }
    
    /**
     * Update adapter status (STARTED/STOPPED/ERROR/etc)
     */
    public void updateStatus(UUID id, Adapter.AdapterStatus status) {
        String sql = """
            UPDATE package_adapters SET
                status = ?,
                updated_at = ?,
                updated_by = ?
            WHERE id = ?
        """;

        UUID updatedBy = getUpdatedByUuid();
        jdbcTemplate.update(sql, status.name(), LocalDateTime.now(), updatedBy, id);

        // Log audit trail for adapter status update
        auditService.logDatabaseOperation("UPDATE", "adapters", id,
            "adapter status updated to " + status.name(), true, null);
    }
    
    /**
     * Delete adapter by ID
     */
    public boolean deleteById(UUID id) {
        // Get adapter info before deletion for audit trail
        Optional<Adapter> adapterOpt = findById(id);
        String adapterName = adapterOpt.map(Adapter::getName).orElse("unknown");
        
        String sql = "DELETE FROM package_adapters WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        // Log audit trail for adapter deletion
        auditService.logDatabaseOperation("DELETE", "adapters", id, 
            adapterName, rowsAffected > 0, rowsAffected == 0 ? "Adapter not found" : null);
        
        return rowsAffected > 0;
    }
    
    /**
     * Find adapters by created user
     */
    public List<Adapter> findByCreatedBy(UUID createdBy) {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE created_by = ?
            ORDER BY created_at DESC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), createdBy);
    }
    
    /**
     * Find adapters by package ID.
     * 
     * @param packageId Package UUID to find adapters for
     * @return List of adapters in the package
     */
    public List<Adapter> findByPackageId(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");

        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE package_id = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), packageId);
    }
    
    /**
     * Count adapters in a package.
     * 
     * @param packageId Package UUID
     * @return Number of adapters in the package
     */
    public long countByPackageId(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        String sql = "SELECT COUNT(*) FROM package_adapters WHERE package_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, packageId);
        return count != null ? count.longValue() : 0L;
    }
    
    /**
     * Find active adapters by package ID.
     * 
     * @param packageId Package UUID
     * @return List of active adapters in the package
     */
    public List<Adapter> findActiveByPackageId(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");

        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE package_id = ? AND active = true
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), packageId);
    }
    
    /**
     * Find adapters by package ID and type.
     *
     * @param packageId Package UUID
     * @param adapterType Adapter type
     * @return List of adapters of specified type in the package
     */
    public List<Adapter> findByPackageIdAndType(UUID packageId, String adapterType) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(adapterType, "Adapter type cannot be null");

        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE package_id = ? AND adapter_type = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), packageId, adapterType);
    }

    /**
     * Find adapter by package ID and name.
     *
     * @param packageId Package UUID
     * @param name Adapter name
     * @return Optional adapter
     */
    public Optional<Adapter> findByPackageIdAndName(UUID packageId, String name) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(name, "Adapter name cannot be null");

        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE package_id = ? AND name = ?
        """;

        try {
            Adapter adapter = jdbcTemplate.queryForObject(sql, new AdapterRowMapper(), packageId, name);
            return Optional.ofNullable(adapter);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Update adapter's package association.
     * 
     * @param adapterId Adapter UUID
     * @param packageId New package UUID
     * @param updatedBy User performing the update
     */
    public void updatePackageAssociation(UUID adapterId, UUID packageId, UUID updatedBy) {
        Objects.requireNonNull(adapterId, "Adapter ID cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(updatedBy, "Updated by cannot be null");
        
        String sql = """
            UPDATE package_adapters SET
                package_id = ?, updated_at = ?, updated_by = ?
            WHERE id = ?
        """;
        
        LocalDateTime now = LocalDateTime.now();
        int rowsAffected = jdbcTemplate.update(sql, packageId, now, updatedBy, adapterId);
        
        if (rowsAffected == 0) {
            throw new IllegalStateException("Adapter not found: " + adapterId);
        }
        
        // Log audit trail for package association update
        auditService.logDatabaseOperation("UPDATE", "package_adapters", adapterId, 
            "package association updated to " + packageId, true, null);
    }
    
    /**
     * Find adapters by active status
     */
    public List<Adapter> findByActive(boolean active) {
        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE active = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), active);
    }
    
    /**
     * Find adapters by package ID and active status
     */
    public List<Adapter> findByPackageIdAndActive(UUID packageId, boolean active) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");

        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE package_id = ? AND active = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), packageId, active);
    }
    
    /**
     * Find adapters by type and package
     */
    public List<Adapter> findByTypeAndPackageId(String adapterType, UUID packageId) {
        Objects.requireNonNull(adapterType, "Adapter type cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");

        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE adapter_type = ? AND package_id = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), adapterType, packageId);
    }
    
    /**
     * Find adapters by type, direction and package
     */
    public List<Adapter> findByTypeAndDirectionAndPackageId(String adapterType, String direction, UUID packageId) {
        Objects.requireNonNull(adapterType, "Adapter type cannot be null");
        Objects.requireNonNull(direction, "Direction cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");

        String sql = """
            SELECT id, name, description, adapter_type, direction, configuration,
                   connection_validated, last_test_at, test_result, active, status,
                   average_execution_time_ms, success_rate_percent, package_id, deployed_from_package_id,
                   created_at, updated_at, created_by, updated_by
            FROM package_adapters
            WHERE adapter_type = ? AND direction = ? AND package_id = ?
            ORDER BY name ASC
        """;

        return jdbcTemplate.query(sql, new AdapterRowMapper(), adapterType, direction, packageId);
    }
    
    /**
     * Check if adapter exists by name and package
     */
    public boolean existsByNameAndPackageId(String name, UUID packageId) {
        Objects.requireNonNull(name, "Name cannot be null");
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        String sql = "SELECT COUNT(*) FROM package_adapters WHERE name = ? AND package_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name, packageId);
        return count != null && count > 0;
    }
    
    /**
     * Count active adapters in package
     */
    public long countByPackageIdAndActive(UUID packageId, boolean active) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        String sql = "SELECT COUNT(*) FROM package_adapters WHERE package_id = ? AND active = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, packageId, active);
        return count != null ? count.longValue() : 0L;
    }
    
    /**
     * Delete adapter by ID (alternative method name)
     */
    public void delete(UUID id) {
        deleteById(id);
    }
    
    /**
     * Get adapter statistics
     */
    public Map<String, Object> getAdapterStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_adapters,
                COUNT(*) FILTER (WHERE active = true) as active_adapters,
                COUNT(*) FILTER (WHERE adapter_type = 'SFTP') as sftp_adapters,
                COUNT(*) FILTER (WHERE adapter_type = 'FILE') as file_adapters,
                COUNT(*) FILTER (WHERE adapter_type = 'EMAIL') as email_adapters,
                COUNT(*) FILTER (WHERE direction = 'SENDER') as sender_adapters,
                COUNT(*) FILTER (WHERE direction = 'RECEIVER') as receiver_adapters,
                COUNT(*) FILTER (WHERE connection_validated = true) as validated_adapters,
                AVG(success_rate_percent) as avg_success_rate,
                AVG(average_execution_time_ms) as avg_execution_time
            FROM package_adapters
        """;
        
        return jdbcTemplate.queryForMap(sql);
    }
    
    /**
     * Row mapper for Adapter entities
     */
    private class AdapterRowMapper implements RowMapper<Adapter> {
        @Override
        public Adapter mapRow(ResultSet rs, int rowNum) throws SQLException {
            Adapter adapter = new Adapter();
            
            adapter.setId(UUID.fromString(rs.getString("id")));
            adapter.setName(rs.getString("name"));
            adapter.setDescription(rs.getString("description"));
            adapter.setAdapterType(rs.getString("adapter_type"));
            adapter.setDirection(rs.getString("direction"));
            adapter.setConfiguration(convertJsonToMap(rs.getString("configuration")));
            adapter.setConnectionValidated(rs.getBoolean("connection_validated"));
            
            Timestamp lastTestAt = rs.getTimestamp("last_test_at");
            if (lastTestAt != null) {
                adapter.setLastTestAt(lastTestAt.toLocalDateTime());
            }
            
            adapter.setTestResult(rs.getString("test_result"));
            adapter.setActive(rs.getBoolean("active"));
            
            String statusStr = rs.getString("status");
            if (statusStr != null) {
                adapter.setStatus(Adapter.AdapterStatus.valueOf(statusStr));
            }
            
            adapter.setAverageExecutionTimeMs(rs.getLong("average_execution_time_ms"));
            
            BigDecimal successRate = rs.getBigDecimal("success_rate_percent");
            if (successRate != null) {
                adapter.setSuccessRatePercent(successRate);
            }
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                adapter.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                adapter.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            String createdBy = rs.getString("created_by");
            if (createdBy != null) {
                adapter.setCreatedBy(UUID.fromString(createdBy));
            }
            
            String updatedBy = rs.getString("updated_by");
            if (updatedBy != null) {
                adapter.setUpdatedBy(UUID.fromString(updatedBy));
            }
            
            // Map package context fields
            String packageId = rs.getString("package_id");
            if (packageId != null) {
                adapter.setPackageId(UUID.fromString(packageId));
            }
            
            String deployedFromPackageId = rs.getString("deployed_from_package_id");
            if (deployedFromPackageId != null) {
                adapter.setDeployedFromPackageId(UUID.fromString(deployedFromPackageId));
            }
            
            return adapter;
        }
    }
    
    /**
     * Convert Map to JSON string for database storage
     */
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null) {
            return "{}";
        }
        
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert map to JSON: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert JSON string to Map for entity mapping
     */
    private Map<String, Object> convertJsonToMap(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert JSON to map: " + e.getMessage(), e);
        }
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