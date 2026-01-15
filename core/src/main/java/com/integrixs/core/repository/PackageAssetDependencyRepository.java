package com.integrixs.core.repository;

import com.integrixs.shared.model.PackageAssetDependency;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PackageAssetDependency entities following OOP principles.
 * Manages dependencies between assets within packages for proper validation
 * and deployment ordering.
 * 
 * @author Claude Code
 * @since Package Management V1.0
 */
@Repository
public class PackageAssetDependencyRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final DependencyRowMapper dependencyRowMapper;
    
    @Autowired
    public PackageAssetDependencyRepository(JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate cannot be null");
        this.auditService = Objects.requireNonNull(auditService, "AuditService cannot be null");
        this.dependencyRowMapper = new DependencyRowMapper();
    }
    
    /**
     * Find all dependencies within a package.
     * 
     * @param packageId Package UUID to find dependencies for
     * @return List of dependencies within the package
     */
    public List<PackageAssetDependency> findByPackageId(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        String sql = """
            SELECT id, package_id, asset_type, asset_id, depends_on_asset_type,
                   depends_on_asset_id, dependency_type, dependency_description,
                   created_at, created_by
            FROM package_asset_dependencies 
            WHERE package_id = ?
            ORDER BY created_at ASC
        """;
        
        return jdbcTemplate.query(sql, dependencyRowMapper, packageId);
    }
    
    /**
     * Find dependencies for a specific asset.
     * 
     * @param packageId Package UUID
     * @param assetType Type of asset
     * @param assetId Asset UUID
     * @return List of dependencies for the asset
     */
    public List<PackageAssetDependency> findDependenciesForAsset(UUID packageId, 
            PackageAssetDependency.AssetType assetType, UUID assetId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(assetType, "Asset type cannot be null");
        Objects.requireNonNull(assetId, "Asset ID cannot be null");
        
        String sql = """
            SELECT id, package_id, asset_type, asset_id, depends_on_asset_type,
                   depends_on_asset_id, dependency_type, dependency_description,
                   created_at, created_by
            FROM package_asset_dependencies 
            WHERE package_id = ? AND asset_type = ? AND asset_id = ?
            ORDER BY dependency_type ASC, created_at ASC
        """;
        
        return jdbcTemplate.query(sql, dependencyRowMapper, packageId, assetType.name(), assetId);
    }
    
    /**
     * Find assets that depend on a specific asset.
     * 
     * @param packageId Package UUID
     * @param dependsOnAssetType Type of asset being depended upon
     * @param dependsOnAssetId Asset UUID being depended upon
     * @return List of assets that depend on the specified asset
     */
    public List<PackageAssetDependency> findAssetsDependingOn(UUID packageId,
            PackageAssetDependency.AssetType dependsOnAssetType, UUID dependsOnAssetId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(dependsOnAssetType, "Depends on asset type cannot be null");
        Objects.requireNonNull(dependsOnAssetId, "Depends on asset ID cannot be null");
        
        String sql = """
            SELECT id, package_id, asset_type, asset_id, depends_on_asset_type,
                   depends_on_asset_id, dependency_type, dependency_description,
                   created_at, created_by
            FROM package_asset_dependencies 
            WHERE package_id = ? AND depends_on_asset_type = ? AND depends_on_asset_id = ?
            ORDER BY dependency_type ASC, created_at ASC
        """;
        
        return jdbcTemplate.query(sql, dependencyRowMapper, packageId, 
            dependsOnAssetType.name(), dependsOnAssetId);
    }
    
    /**
     * Find required dependencies for a specific asset.
     * 
     * @param packageId Package UUID
     * @param assetType Type of asset
     * @param assetId Asset UUID
     * @return List of required dependencies for the asset
     */
    public List<PackageAssetDependency> findRequiredDependenciesForAsset(UUID packageId,
            PackageAssetDependency.AssetType assetType, UUID assetId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(assetType, "Asset type cannot be null");
        Objects.requireNonNull(assetId, "Asset ID cannot be null");
        
        String sql = """
            SELECT id, package_id, asset_type, asset_id, depends_on_asset_type,
                   depends_on_asset_id, dependency_type, dependency_description,
                   created_at, created_by
            FROM package_asset_dependencies 
            WHERE package_id = ? AND asset_type = ? AND asset_id = ? AND dependency_type = 'REQUIRED'
            ORDER BY created_at ASC
        """;
        
        return jdbcTemplate.query(sql, dependencyRowMapper, packageId, assetType.name(), assetId);
    }
    
    /**
     * Find dependency by ID.
     * 
     * @param id Dependency UUID
     * @return Optional containing dependency if found
     */
    public Optional<PackageAssetDependency> findById(UUID id) {
        Objects.requireNonNull(id, "Dependency ID cannot be null");
        
        String sql = """
            SELECT id, package_id, asset_type, asset_id, depends_on_asset_type,
                   depends_on_asset_id, dependency_type, dependency_description,
                   created_at, created_by
            FROM package_asset_dependencies 
            WHERE id = ?
        """;
        
        try {
            PackageAssetDependency dependency = jdbcTemplate.queryForObject(sql, dependencyRowMapper, id);
            return Optional.of(dependency);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if a specific dependency relationship exists.
     * 
     * @param packageId Package UUID
     * @param assetType Type of dependent asset
     * @param assetId Dependent asset UUID
     * @param dependsOnAssetType Type of asset being depended upon
     * @param dependsOnAssetId Asset UUID being depended upon
     * @return true if dependency exists, false otherwise
     */
    public boolean existsDependency(UUID packageId, PackageAssetDependency.AssetType assetType, 
            UUID assetId, PackageAssetDependency.AssetType dependsOnAssetType, UUID dependsOnAssetId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(assetType, "Asset type cannot be null");
        Objects.requireNonNull(assetId, "Asset ID cannot be null");
        Objects.requireNonNull(dependsOnAssetType, "Depends on asset type cannot be null");
        Objects.requireNonNull(dependsOnAssetId, "Depends on asset ID cannot be null");
        
        String sql = """
            SELECT COUNT(*) 
            FROM package_asset_dependencies 
            WHERE package_id = ? AND asset_type = ? AND asset_id = ? 
              AND depends_on_asset_type = ? AND depends_on_asset_id = ?
        """;
        
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, 
            packageId, assetType.name(), assetId, dependsOnAssetType.name(), dependsOnAssetId);
        
        return count != null && count > 0;
    }
    
    /**
     * Save new dependency to database.
     * 
     * @param dependency Dependency to save
     * @return Generated UUID of saved dependency
     * @throws IllegalArgumentException if dependency is invalid
     */
    public UUID save(PackageAssetDependency dependency) {
        Objects.requireNonNull(dependency, "Dependency cannot be null");
        validateDependencyForSave(dependency);
        
        if (dependency.getId() == null) {
            dependency.setId(UUID.randomUUID());
        }
        
        // Set audit fields for INSERT
        LocalDateTime now = LocalDateTime.now();
        dependency.setCreatedAt(now);
        
        UUID createdBy = determineCreatedBy(dependency);
        dependency.setCreatedBy(createdBy);
        
        String sql = """
            INSERT INTO package_asset_dependencies (
                id, package_id, asset_type, asset_id, depends_on_asset_type,
                depends_on_asset_id, dependency_type, dependency_description,
                created_at, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            dependency.getId(),
            dependency.getPackageId(),
            dependency.getAssetType().name(),
            dependency.getAssetId(),
            dependency.getDependsOnAssetType().name(),
            dependency.getDependsOnAssetId(),
            dependency.getDependencyType().name(),
            dependency.getDependencyDescription(),
            dependency.getCreatedAt(),
            createdBy
        );
        
        // Log audit trail for dependency creation
        auditService.logDatabaseOperation("INSERT", "package_asset_dependencies", dependency.getId(), 
            dependency.getDependencyLabel(), true, null);
        
        return dependency.getId();
    }
    
    /**
     * Update existing dependency description and type.
     * 
     * @param dependency Dependency to update
     * @throws IllegalArgumentException if dependency is invalid
     * @throws IllegalStateException if dependency doesn't exist
     */
    public void update(PackageAssetDependency dependency) {
        Objects.requireNonNull(dependency, "Dependency cannot be null");
        Objects.requireNonNull(dependency.getId(), "Dependency ID cannot be null for update");
        
        String sql = """
            UPDATE package_asset_dependencies SET
                dependency_type = ?, dependency_description = ?
            WHERE id = ?
        """;
        
        int rowsAffected = jdbcTemplate.update(sql,
            dependency.getDependencyType().name(),
            dependency.getDependencyDescription(),
            dependency.getId()
        );
        
        if (rowsAffected == 0) {
            throw new IllegalStateException("Dependency not found: " + dependency.getId());
        }
        
        // Log audit trail for dependency update
        auditService.logDatabaseOperation("UPDATE", "package_asset_dependencies", dependency.getId(), 
            dependency.getDependencyLabel(), true, null);
    }
    
    /**
     * Delete dependency by ID.
     * 
     * @param id Dependency UUID to delete
     * @return true if dependency was deleted, false if not found
     */
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Dependency ID cannot be null");
        
        // Get dependency info before deletion for audit trail
        Optional<PackageAssetDependency> depOpt = findById(id);
        String dependencyLabel = depOpt.map(PackageAssetDependency::getDependencyLabel).orElse("unknown");
        
        String sql = "DELETE FROM package_asset_dependencies WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        
        // Log audit trail for dependency deletion
        auditService.logDatabaseOperation("DELETE", "package_asset_dependencies", id, 
            dependencyLabel, rowsAffected > 0, rowsAffected == 0 ? "Dependency not found" : null);
        
        return rowsAffected > 0;
    }
    
    /**
     * Delete all dependencies for a specific asset.
     * 
     * @param packageId Package UUID
     * @param assetType Type of asset
     * @param assetId Asset UUID
     * @return Number of dependencies deleted
     */
    public int deleteAllForAsset(UUID packageId, PackageAssetDependency.AssetType assetType, UUID assetId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        Objects.requireNonNull(assetType, "Asset type cannot be null");
        Objects.requireNonNull(assetId, "Asset ID cannot be null");
        
        String sql = """
            DELETE FROM package_asset_dependencies 
            WHERE package_id = ? AND asset_type = ? AND asset_id = ?
        """;
        
        int rowsAffected = jdbcTemplate.update(sql, packageId, assetType.name(), assetId);
        
        // Log audit trail for bulk dependency deletion
        auditService.logDatabaseOperation("BULK_DELETE", "package_asset_dependencies", assetId, 
            String.format("All dependencies for %s %s", assetType.name(), assetId), 
            rowsAffected > 0, rowsAffected == 0 ? "No dependencies found" : null);
        
        return rowsAffected;
    }
    
    /**
     * Delete all dependencies within a package.
     * 
     * @param packageId Package UUID
     * @return Number of dependencies deleted
     */
    public int deleteAllForPackage(UUID packageId) {
        Objects.requireNonNull(packageId, "Package ID cannot be null");
        
        String sql = "DELETE FROM package_asset_dependencies WHERE package_id = ?";
        int rowsAffected = jdbcTemplate.update(sql, packageId);
        
        // Log audit trail for package dependency cleanup
        auditService.logDatabaseOperation("BULK_DELETE", "package_asset_dependencies", packageId, 
            "All dependencies for package " + packageId, rowsAffected > 0, 
            rowsAffected == 0 ? "No dependencies found" : null);
        
        return rowsAffected;
    }
    
    // Private helper methods following OOP encapsulation principles
    
    /**
     * Validate dependency for save operation.
     */
    private void validateDependencyForSave(PackageAssetDependency dependency) {
        if (dependency.getPackageId() == null) {
            throw new IllegalArgumentException("Package ID cannot be null");
        }
        
        if (dependency.getAssetType() == null || dependency.getAssetId() == null) {
            throw new IllegalArgumentException("Asset type and ID cannot be null");
        }
        
        if (dependency.getDependsOnAssetType() == null || dependency.getDependsOnAssetId() == null) {
            throw new IllegalArgumentException("Depends on asset type and ID cannot be null");
        }
        
        // Check for self-dependency
        if (dependency.getAssetType() == dependency.getDependsOnAssetType() && 
            dependency.getAssetId().equals(dependency.getDependsOnAssetId())) {
            throw new IllegalArgumentException("Asset cannot depend on itself");
        }
        
        // Check if dependency already exists
        if (existsDependency(dependency.getPackageId(), dependency.getAssetType(), 
                dependency.getAssetId(), dependency.getDependsOnAssetType(), 
                dependency.getDependsOnAssetId())) {
            throw new IllegalArgumentException("Dependency relationship already exists");
        }
    }
    
    /**
     * Determine created by user for audit trail.
     */
    private UUID determineCreatedBy(PackageAssetDependency dependency) {
        try {
            String currentUserId = AuditUtils.getCurrentUserId();
            return UUID.fromString(currentUserId);
        } catch (IllegalArgumentException e) {
            // Fallback to dependency's createdBy if available
            if (dependency.getCreatedBy() != null) {
                return dependency.getCreatedBy();
            }
            throw new RuntimeException("Unable to determine valid user ID for audit trail");
        }
    }
    
    /**
     * Row mapper for PackageAssetDependency entities.
     */
    private static class DependencyRowMapper implements RowMapper<PackageAssetDependency> {
        @Override
        public PackageAssetDependency mapRow(ResultSet rs, int rowNum) throws SQLException {
            PackageAssetDependency dependency = new PackageAssetDependency();
            
            dependency.setId(UUID.fromString(rs.getString("id")));
            dependency.setPackageId(UUID.fromString(rs.getString("package_id")));
            
            // Map asset information
            String assetType = rs.getString("asset_type");
            if (assetType != null) {
                dependency.setAssetType(PackageAssetDependency.AssetType.valueOf(assetType));
            }
            dependency.setAssetId(UUID.fromString(rs.getString("asset_id")));
            
            // Map dependency information
            String dependsOnAssetType = rs.getString("depends_on_asset_type");
            if (dependsOnAssetType != null) {
                dependency.setDependsOnAssetType(PackageAssetDependency.AssetType.valueOf(dependsOnAssetType));
            }
            dependency.setDependsOnAssetId(UUID.fromString(rs.getString("depends_on_asset_id")));
            
            // Map dependency metadata
            String dependencyType = rs.getString("dependency_type");
            if (dependencyType != null) {
                dependency.setDependencyType(PackageAssetDependency.DependencyType.valueOf(dependencyType));
            }
            dependency.setDependencyDescription(rs.getString("dependency_description"));
            
            // Map audit fields
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                dependency.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            String createdBy = rs.getString("created_by");
            if (createdBy != null) {
                dependency.setCreatedBy(UUID.fromString(createdBy));
            }
            
            return dependency;
        }
    }
}