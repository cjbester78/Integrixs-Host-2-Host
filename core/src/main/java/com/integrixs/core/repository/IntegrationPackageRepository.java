package com.integrixs.core.repository;

import com.integrixs.shared.model.IntegrationPackage;
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

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Repository for IntegrationPackage entities following SOLID principles.
 * Provides encapsulated data access operations for package management.
 * 
 * @author Claude Code
 * @since Package Management V1.0
 */
@Repository
public class IntegrationPackageRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final PackageRowMapper packageRowMapper;
    
    @Autowired
    public IntegrationPackageRepository(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
        this.auditService = Objects.requireNonNull(auditService, "AuditService cannot be null");
        this.objectMapper = new ObjectMapper();
        this.packageRowMapper = new PackageRowMapper();
    }
    
    /**
     * Find all active packages.
     * 
     * @return List of active integration packages ordered by creation date
     */
    public List<IntegrationPackage> findAllActive() {
        String sql = """
            SELECT id, name, description, version, status,
                   configuration, created_at, updated_at, created_by, updated_by
            FROM integration_packages 
            ORDER BY created_at DESC
        """;
        
        return jdbcTemplate.query(sql, packageRowMapper);
    }
    
    /**
     * Find packages by status.
     * 
     * @param status Package status to filter by
     * @return List of packages with specified status
     */
    public List<IntegrationPackage> findByStatus(IntegrationPackage.PackageStatus status) {
        Objects.requireNonNull(status, "Status cannot be null");
        
        String sql = """
            SELECT id, name, description, version, status,
                   configuration, created_at, updated_at, created_by, updated_by
            FROM integration_packages 
            WHERE status = ?
            ORDER BY name ASC
        """;
        
        return jdbcTemplate.query(sql, packageRowMapper, status.name());
    }
    
    /**
     * Find package by ID.
     * 
     * @param id Package UUID
     * @return Optional containing package if found
     */
    public Optional<IntegrationPackage> findById(UUID id) {
        Objects.requireNonNull(id, "Package ID cannot be null");
        
        String sql = """
            SELECT id, name, description, version, status,
                   configuration, created_at, updated_at, created_by, updated_by
            FROM integration_packages 
            WHERE id = ?
        """;
        
        try {
            IntegrationPackage pkg = jdbcTemplate.queryForObject(sql, packageRowMapper, id);
            return Optional.of(pkg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find package by name (case-sensitive).
     * 
     * @param name Package name
     * @return Optional containing package if found
     */
    public Optional<IntegrationPackage> findByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        
        String sql = """
            SELECT id, name, description, version, status,
                   configuration, created_at, updated_at, created_by, updated_by
            FROM integration_packages 
            WHERE name = ?
        """;
        
        try {
            IntegrationPackage pkg = jdbcTemplate.queryForObject(sql, packageRowMapper, name.trim());
            return Optional.of(pkg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if package name exists.
     * 
     * @param name Package name to check
     * @return true if name exists, false otherwise
     */
    public boolean existsByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM integration_packages WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name.trim());
        return count != null && count > 0;
    }
    
    /**
     * Check if package name exists excluding specific ID.
     * 
     * @param name Package name to check
     * @param excludeId Package ID to exclude from check
     * @return true if name exists for different package, false otherwise
     */
    public boolean existsByNameAndNotId(String name, UUID excludeId) {
        Objects.requireNonNull(excludeId, "Exclude ID cannot be null");
        
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        String sql = "SELECT COUNT(*) FROM integration_packages WHERE name = ? AND id != ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name.trim(), excludeId);
        return count != null && count > 0;
    }
    
    /**
     * Save new package to database.
     * 
     * @param pkg Package to save
     * @return Generated UUID of saved package
     * @throws IllegalArgumentException if package is invalid
     */
    public UUID save(IntegrationPackage pkg) {
        Objects.requireNonNull(pkg, "Package cannot be null");
        validatePackageForSave(pkg);
        
        if (pkg.getId() == null) {
            pkg.setId(UUID.randomUUID());
        }
        
        // Set audit fields for INSERT
        LocalDateTime now = LocalDateTime.now();
        pkg.setCreatedAt(now);
        // Don't set updated_at for new records - that's only for updates
        
        UUID createdBy = determineCreatedBy(pkg);
        pkg.setCreatedBy(createdBy);
        
        String sql = """
            INSERT INTO integration_packages (
                id, name, description, version, status,
                configuration, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            pkg.getId(),
            pkg.getName(),
            pkg.getDescription(),
            pkg.getVersion(),
            pkg.getStatus().name(),
            convertMapToJson(pkg.getConfiguration()),
            pkg.getCreatedAt(),
            createdBy
        );
        
        // Log audit trail for package creation
        auditService.logDatabaseOperation("INSERT", "integration_packages", pkg.getId(), 
            pkg.getName(), true, null);
        
        return pkg.getId();
    }
    
    /**
     * Update existing package in database.
     * 
     * @param pkg Package to update
     * @throws IllegalArgumentException if package is invalid
     * @throws IllegalStateException if package doesn't exist
     */
    public void update(IntegrationPackage pkg) {
        Objects.requireNonNull(pkg, "Package cannot be null");
        Objects.requireNonNull(pkg.getId(), "Package ID cannot be null for update");
        validatePackageForUpdate(pkg);
        
        pkg.setUpdatedAt(LocalDateTime.now());
        UUID updatedBy = determineUpdatedBy(pkg);
        pkg.setUpdatedBy(updatedBy);
        
        String sql = """
            UPDATE integration_packages SET
                name = ?, description = ?, version = ?, status = ?,
                configuration = ?::jsonb, updated_at = ?, updated_by = ?
            WHERE id = ?
        """;
        
        int rowsAffected = jdbcTemplate.update(sql,
            pkg.getName(),
            pkg.getDescription(),
            pkg.getVersion(),
            pkg.getStatus().name(),
            convertMapToJson(pkg.getConfiguration()),
            pkg.getUpdatedAt(),
            updatedBy,
            pkg.getId()
        );
        
        if (rowsAffected == 0) {
            throw new IllegalStateException("Package not found or already deleted: " + pkg.getId());
        }
        
        // Log audit trail for package update
        auditService.logDatabaseOperation("UPDATE", "integration_packages", pkg.getId(), 
            pkg.getName(), true, null);
    }
    
    
    /**
     * Delete package from database.
     * Use with caution - this operation cannot be undone.
     * 
     * @param id Package ID to delete
     * @return true if package was deleted, false if not found
     */
    public boolean deletePackage(UUID id) {
        Objects.requireNonNull(id, "Package ID cannot be null");
        
        // Get package info before deletion for audit trail
        Optional<IntegrationPackage> pkgOpt = findById(id);
        String packageName = pkgOpt.map(IntegrationPackage::getName).orElse("unknown");
        
        String sql = "DELETE FROM integration_packages WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        // Log audit trail for package deletion
        auditService.logDatabaseOperation("HARD_DELETE", "integration_packages", id, 
            packageName, rowsAffected > 0, rowsAffected == 0 ? "Package not found" : null);
        
        return rowsAffected > 0;
    }
    
    /**
     * Get package statistics for dashboard.
     * 
     * @return Map containing package statistics
     */
    public Map<String, Object> getPackageStatistics() {
        String sql = """
            SELECT
                COUNT(*) as total_packages,
                COUNT(*) FILTER (WHERE status = 'ACTIVE') as active_packages
            FROM integration_packages
        """;

        return jdbcTemplate.queryForMap(sql);
    }
    
    /**
     * Find packages created by specific user.
     * 
     * @param createdBy User UUID
     * @return List of packages created by user
     */
    public List<IntegrationPackage> findByCreatedBy(UUID createdBy) {
        Objects.requireNonNull(createdBy, "Created by cannot be null");
        
        String sql = """
            SELECT id, name, description, version, status,
                   configuration, created_at, updated_at, created_by, updated_by
            FROM integration_packages 
            WHERE created_by = ?
            ORDER BY created_at DESC
        """;
        
        return jdbcTemplate.query(sql, packageRowMapper, createdBy);
    }
    
    // Private helper methods following OOP encapsulation principles
    
    /**
     * Validate package for save operation.
     */
    private void validatePackageForSave(IntegrationPackage pkg) {
        if (pkg.getName() == null || pkg.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Package name cannot be null or empty");
        }
        
        if (existsByName(pkg.getName())) {
            throw new IllegalArgumentException("Package name already exists: " + pkg.getName());
        }
    }
    
    /**
     * Validate package for update operation.
     */
    private void validatePackageForUpdate(IntegrationPackage pkg) {
        if (pkg.getName() == null || pkg.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Package name cannot be null or empty");
        }
        
        if (existsByNameAndNotId(pkg.getName(), pkg.getId())) {
            throw new IllegalArgumentException("Package name already exists: " + pkg.getName());
        }
    }
    
    /**
     * Determine created by user for audit trail.
     */
    private UUID determineCreatedBy(IntegrationPackage pkg) {
        try {
            String currentUserId = AuditUtils.getCurrentUserId();
            return UUID.fromString(currentUserId);
        } catch (IllegalArgumentException e) {
            // Fallback to package's createdBy if available
            if (pkg.getCreatedBy() != null) {
                return pkg.getCreatedBy();
            }
            throw new RuntimeException("Unable to determine valid user ID for audit trail");
        }
    }
    
    /**
     * Determine updated by user for audit trail.
     */
    private UUID determineUpdatedBy(IntegrationPackage pkg) {
        try {
            String currentUserId = AuditUtils.getCurrentUserId();
            return UUID.fromString(currentUserId);
        } catch (IllegalArgumentException e) {
            // Fallback to package's updatedBy if available
            if (pkg.getUpdatedBy() != null) {
                return pkg.getUpdatedBy();
            }
            throw new RuntimeException("Unable to determine valid user ID for audit trail");
        }
    }
    
    /**
     * Convert Map to JSON string for database storage.
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
     * Convert JSON string to Map for entity mapping.
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
     * Convert List to Array for database storage.
     */
    private String[] convertListToArray(List<String> list) {
        if (list == null) {
            return new String[0];
        }
        return list.toArray(new String[0]);
    }
    
    /**
     * Convert Array to List from database.
     */
    private List<String> convertArrayToList(Array array) {
        if (array == null) {
            return new ArrayList<>();
        }
        
        try {
            String[] stringArray = (String[]) array.getArray();
            return new ArrayList<>(Arrays.asList(stringArray));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to convert array to list: " + e.getMessage(), e);
        }
    }
    
    /**
     * Row mapper for IntegrationPackage entities following single responsibility principle.
     */
    private class PackageRowMapper implements RowMapper<IntegrationPackage> {
        @Override
        public IntegrationPackage mapRow(ResultSet rs, int rowNum) throws SQLException {
            IntegrationPackage pkg = new IntegrationPackage();
            
            pkg.setId(UUID.fromString(rs.getString("id")));
            pkg.setName(rs.getString("name"));
            pkg.setDescription(rs.getString("description"));
            pkg.setVersion(rs.getString("version"));
            
            // Map enums safely
            String status = rs.getString("status");
            if (status != null) {
                pkg.setStatus(IntegrationPackage.PackageStatus.valueOf(status));
            }
            
            // Map complex types
            pkg.setConfiguration(convertJsonToMap(rs.getString("configuration")));
            
            // Package type, tags, and soft delete fields removed
            
            // Map audit fields
            mapAuditFields(rs, pkg);
            
            return pkg;
        }
        
        /**
         * Map audit fields to package entity.
         */
        private void mapAuditFields(ResultSet rs, IntegrationPackage pkg) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                pkg.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                pkg.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            String createdBy = rs.getString("created_by");
            if (createdBy != null) {
                pkg.setCreatedBy(UUID.fromString(createdBy));
            }
            
            String updatedBy = rs.getString("updated_by");
            if (updatedBy != null) {
                pkg.setUpdatedBy(UUID.fromString(updatedBy));
            }
        }
    }
}