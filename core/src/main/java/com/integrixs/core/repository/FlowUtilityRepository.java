package com.integrixs.core.repository;

import com.integrixs.shared.model.FlowUtility;
import com.integrixs.shared.util.AuditUtils;
import com.integrixs.shared.service.SystemAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC Repository for flow utility management
 * Handles CRUD operations for available utilities (PGP, ZIP, etc.)
 */
@Repository
public class FlowUtilityRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final SystemAuditService auditService;
    
    @Autowired
    public FlowUtilityRepository(JdbcTemplate jdbcTemplate, SystemAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }
    
    /**
     * Find all utilities
     */
    public List<FlowUtility> findAll() {
        String sql = """
            SELECT id, name, utility_type, description, configuration_schema,
                   default_configuration, supports_parallel, max_file_size_mb, 
                   supported_formats, active, created_at, updated_at, created_by
            FROM flow_utilities 
            ORDER BY name ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowUtilityRowMapper());
    }
    
    /**
     * Find utilities by type
     */
    public List<FlowUtility> findByType(String utilityType) {
        String sql = """
            SELECT id, name, utility_type, description, configuration_schema,
                   default_configuration, supports_parallel, max_file_size_mb, 
                   supported_formats, active, created_at, updated_at, created_by
            FROM flow_utilities 
            WHERE utility_type = ?
            ORDER BY name ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowUtilityRowMapper(), utilityType);
    }
    
    /**
     * Find active utilities
     */
    public List<FlowUtility> findAllActive() {
        String sql = """
            SELECT id, name, utility_type, description, configuration_schema,
                   default_configuration, supports_parallel, max_file_size_mb, 
                   supported_formats, active, created_at, updated_at, created_by
            FROM flow_utilities 
            WHERE active = true
            ORDER BY name ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowUtilityRowMapper());
    }
    
    /**
     * Find utilities by supported file type
     */
    public List<FlowUtility> findBySupportedFileType(String fileType) {
        String sql = """
            SELECT id, name, utility_type, description, configuration_schema,
                   default_configuration, supports_parallel, max_file_size_mb, 
                   supported_formats, active, created_at, updated_at, created_by
            FROM flow_utilities 
            WHERE active = true 
              AND (supported_formats IS NULL 
                   OR ? = ANY(supported_formats))
            ORDER BY name ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowUtilityRowMapper(), fileType);
    }
    
    /**
     * Find utilities that support parallel processing
     */
    public List<FlowUtility> findParallelUtilities() {
        String sql = """
            SELECT id, name, utility_type, description, configuration_schema,
                   default_configuration, supports_parallel, max_file_size_mb, 
                   supported_formats, active, created_at, updated_at, created_by
            FROM flow_utilities 
            WHERE active = true 
              AND supports_parallel = true
            ORDER BY name ASC
        """;
        
        return jdbcTemplate.query(sql, new FlowUtilityRowMapper());
    }
    
    /**
     * Find utility by ID
     */
    public Optional<FlowUtility> findById(UUID id) {
        String sql = """
            SELECT id, name, utility_type, description, configuration_schema,
                   default_configuration, supports_parallel, max_file_size_mb, 
                   supported_formats, active, created_at, updated_at, created_by
            FROM flow_utilities 
            WHERE id = ?
        """;
        
        try {
            FlowUtility utility = jdbcTemplate.queryForObject(sql, new FlowUtilityRowMapper(), id);
            return Optional.of(utility);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find utility by name
     */
    public Optional<FlowUtility> findByName(String name) {
        String sql = """
            SELECT id, name, utility_type, description, configuration_schema,
                   default_configuration, supports_parallel, max_file_size_mb, 
                   supported_formats, active, created_at, updated_at, created_by
            FROM flow_utilities 
            WHERE name = ?
        """;
        
        try {
            FlowUtility utility = jdbcTemplate.queryForObject(sql, new FlowUtilityRowMapper(), name);
            return Optional.of(utility);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if utility exists by name
     */
    public boolean existsByName(String name) {
        String sql = "SELECT COUNT(*) FROM flow_utilities WHERE name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, name);
        return count != null && count > 0;
    }
    
    /**
     * Save utility (insert)
     */
    public UUID save(FlowUtility utility) {
        if (utility.getId() == null) {
            utility.setId(UUID.randomUUID());
        }
        
        // Set audit fields for INSERT
        utility.setCreatedAt(LocalDateTime.now());
        // Don't set updated_at for new records
        
        String createdBy = AuditUtils.getCurrentUserId();
        
        String sql = """
            INSERT INTO flow_utilities (
                id, name, utility_type, description, configuration_schema,
                default_configuration, supports_parallel, max_file_size_mb, 
                supported_formats, active, created_at, created_by
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
        """;
        
        jdbcTemplate.update(sql,
            utility.getId(),
            utility.getName(),
            utility.getUtilityType().name(),
            utility.getDescription(),
            convertMapToJson(utility.getConfigurationSchema()),
            convertMapToJson(utility.getDefaultConfiguration()),
            utility.getSupportsParallel(),
            utility.getMaxFileSizeMb(),
            utility.getSupportedFormats() != null ? utility.getSupportedFormats().toArray(new String[0]) : null,
            utility.getActive(),
            utility.getCreatedAt(),
            createdBy
        );
        
        // Log audit trail for utility creation
        auditService.logDatabaseOperation("INSERT", "flow_utilities", utility.getId(), 
            utility.getName(), true, null);
        
        return utility.getId();
    }
    
    /**
     * Update utility
     */
    public void update(FlowUtility utility) {
        utility.setUpdatedAt(LocalDateTime.now());
        String updatedBy = AuditUtils.getCurrentUserId();
        
        String sql = """
            UPDATE flow_utilities SET
                name = ?, utility_type = ?, description = ?,
                configuration_schema = ?::jsonb, default_configuration = ?::jsonb,
                supports_parallel = ?, max_file_size_mb = ?, supported_formats = ?,
                active = ?, updated_at = ?, updated_by = ?
            WHERE id = ?
        """;
        
        int rowsAffected = jdbcTemplate.update(sql,
            utility.getName(),
            utility.getUtilityType().name(),
            utility.getDescription(),
            convertMapToJson(utility.getConfigurationSchema()),
            convertMapToJson(utility.getDefaultConfiguration()),
            utility.getSupportsParallel(),
            utility.getMaxFileSizeMb(),
            utility.getSupportedFormats() != null ? utility.getSupportedFormats().toArray(new String[0]) : null,
            utility.getActive(),
            utility.getUpdatedAt(),
            updatedBy,
            utility.getId()
        );
        
        // Log audit trail for utility update
        auditService.logDatabaseOperation("UPDATE", "flow_utilities", utility.getId(), 
            utility.getName(), rowsAffected > 0, rowsAffected == 0 ? "Utility not found" : null);
    }
    
    /**
     * Update utility configuration schema
     */
    public void updateConfigurationSchema(UUID id, Map<String, Object> configurationSchema) {
        String sql = """
            UPDATE flow_utilities SET
                configuration_schema = ?::jsonb,
                updated_at = ?,
                updated_by = ?
            WHERE id = ?
        """;

        UUID updatedBy = getUpdatedByUuid();
        jdbcTemplate.update(sql, convertMapToJson(configurationSchema), LocalDateTime.now(), updatedBy, id);
    }

    /**
     * Update utility default configuration
     */
    public void updateDefaultConfiguration(UUID id, Map<String, Object> defaultConfiguration) {
        String sql = """
            UPDATE flow_utilities SET
                default_configuration = ?::jsonb,
                updated_at = ?,
                updated_by = ?
            WHERE id = ?
        """;

        UUID updatedBy = getUpdatedByUuid();
        jdbcTemplate.update(sql, convertMapToJson(defaultConfiguration), LocalDateTime.now(), updatedBy, id);
    }

    /**
     * Set utility active status
     */
    public void setActive(UUID id, boolean active) {
        String sql = """
            UPDATE flow_utilities SET
                active = ?,
                updated_at = ?,
                updated_by = ?
            WHERE id = ?
        """;

        UUID updatedBy = getUpdatedByUuid();
        jdbcTemplate.update(sql, active, LocalDateTime.now(), updatedBy, id);
    }
    
    /**
     * Delete utility by ID
     */
    public boolean deleteById(UUID id) {
        String sql = "DELETE FROM flow_utilities WHERE id = ?";
        int rowsAffected = jdbcTemplate.update(sql, id);
        return rowsAffected > 0;
    }
    
    /**
     * Get utility statistics
     */
    public Map<String, Object> getUtilityStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_utilities,
                COUNT(*) FILTER (WHERE active = true) as active_utilities,
                COUNT(*) FILTER (WHERE utility_type = 'PGP_ENCRYPT' OR utility_type = 'PGP_DECRYPT') as pgp_utilities,
                COUNT(*) FILTER (WHERE utility_type = 'ZIP_COMPRESS' OR utility_type = 'ZIP_EXTRACT') as zip_utilities,
                COUNT(*) FILTER (WHERE utility_type = 'DATA_TRANSFORM') as transform_utilities,
                COUNT(*) FILTER (WHERE utility_type = 'FILE_VALIDATE') as validate_utilities,
                COUNT(*) FILTER (WHERE supports_parallel = true) as parallel_utilities
            FROM flow_utilities
        """;
        
        return jdbcTemplate.queryForMap(sql);
    }
    
    /**
     * Row mapper for FlowUtility entities
     */
    private static class FlowUtilityRowMapper implements RowMapper<FlowUtility> {
        @Override
        public FlowUtility mapRow(ResultSet rs, int rowNum) throws SQLException {
            FlowUtility utility = new FlowUtility();
            
            utility.setId(UUID.fromString(rs.getString("id")));
            utility.setName(rs.getString("name"));
            utility.setUtilityType(FlowUtility.UtilityType.valueOf(rs.getString("utility_type")));
            utility.setDescription(rs.getString("description"));
            utility.setConfigurationSchema(convertJsonToMap(rs.getString("configuration_schema")));
            utility.setDefaultConfiguration(convertJsonToMap(rs.getString("default_configuration")));
            utility.setSupportsParallel(rs.getBoolean("supports_parallel"));
            utility.setMaxFileSizeMb(rs.getInt("max_file_size_mb"));
            
            // Handle TEXT[] array field
            java.sql.Array supportedFormatsArray = rs.getArray("supported_formats");
            if (supportedFormatsArray != null) {
                String[] formatsArray = (String[]) supportedFormatsArray.getArray();
                utility.setSupportedFormats(Arrays.asList(formatsArray));
            }
            
            utility.setActive(rs.getBoolean("active"));
            
            Timestamp createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                utility.setCreatedAt(createdAt.toLocalDateTime());
            }
            
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                utility.setUpdatedAt(updatedAt.toLocalDateTime());
            }
            
            String createdBy = rs.getString("created_by");
            if (createdBy != null) {
                utility.setCreatedBy(UUID.fromString(createdBy));
            }
            
            return utility;
        }
    }
    
    /**
     * Convert Map to JSON string for database storage
     */
    private String convertMapToJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        try {
            // Simple JSON conversion - in production use Jackson ObjectMapper
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else if (entry.getValue() instanceof Map) {
                    json.append(convertMapToJson((Map<String, Object>) entry.getValue()));
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convert JSON string to Map for entity mapping
     */
    private static Map<String, Object> convertJsonToMap(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new java.util.HashMap<>();
        }
        
        try {
            // Simple JSON parsing - in production use Jackson ObjectMapper
            Map<String, Object> map = new java.util.HashMap<>();
            
            // Remove braces and split by comma (basic implementation)
            String content = json.substring(1, json.length() - 1);
            if (content.trim().isEmpty()) {
                return map;
            }
            
            // Very basic parsing for now - would use Jackson in production
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replace("\"", "");
                    String value = keyValue[1].trim().replace("\"", "");
                    map.put(key, value);
                }
            }
            
            return map;
        } catch (Exception e) {
            return new java.util.HashMap<>();
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