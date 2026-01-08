package com.integrixs.core.repository;

import com.integrixs.shared.model.SystemConfiguration;
import com.integrixs.shared.util.AuditUtils;
import com.integrixs.shared.service.SystemAuditService;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repository for system configuration settings
 * Provides efficient access to application configuration stored in database
 */
@Repository
public class SystemConfigurationRepository {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final SystemAuditService auditService;

    public SystemConfigurationRepository(JdbcTemplate jdbcTemplate, SystemAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    /**
     * Find configuration by key
     */
    public Optional<SystemConfiguration> findByKey(String configKey) {
        try {
            String sql = """
                SELECT id, config_key, config_value, config_type, description, category,
                       is_encrypted, is_readonly, default_value, created_at, updated_at,
                       created_by, updated_by
                FROM system_configuration 
                WHERE config_key = ?
                """;
            
            List<SystemConfiguration> results = jdbcTemplate.query(sql, configurationRowMapper(), configKey);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));

        } catch (Exception e) {
            logger.error("Error finding configuration by key '{}': {}", configKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get configuration value by key, with fallback to default
     */
    public String getValue(String configKey, String defaultValue) {
        return findByKey(configKey)
                .map(SystemConfiguration::getConfigValue)
                .orElse(defaultValue);
    }

    /**
     * Get integer configuration value
     */
    public Integer getIntegerValue(String configKey, Integer defaultValue) {
        try {
            return findByKey(configKey)
                    .map(SystemConfiguration::getIntegerValue)
                    .orElse(defaultValue);
        } catch (Exception e) {
            logger.warn("Error parsing integer value for key '{}', using default: {}", configKey, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get boolean configuration value
     */
    public Boolean getBooleanValue(String configKey, Boolean defaultValue) {
        try {
            return findByKey(configKey)
                    .map(SystemConfiguration::getBooleanValue)
                    .orElse(defaultValue);
        } catch (Exception e) {
            logger.warn("Error parsing boolean value for key '{}', using default: {}", configKey, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Find all configurations by category
     */
    public List<SystemConfiguration> findByCategory(SystemConfiguration.ConfigCategory category) {
        try {
            String sql = """
                SELECT id, config_key, config_value, config_type, description, category,
                       is_encrypted, is_readonly, default_value, created_at, updated_at,
                       created_by, updated_by
                FROM system_configuration 
                WHERE category = ?
                ORDER BY config_key
                """;
            
            return jdbcTemplate.query(sql, configurationRowMapper(), category.name());

        } catch (Exception e) {
            logger.error("Error finding configurations by category '{}': {}", category, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all configurations as a Map for easy lookup
     */
    public Map<String, String> getAllConfigurationValues() {
        try {
            String sql = """
                SELECT config_key, config_value
                FROM system_configuration
                ORDER BY config_key
                """;
            
            return jdbcTemplate.query(sql, (rs) -> {
                Map<String, String> configMap = new java.util.HashMap<>();
                while (rs.next()) {
                    configMap.put(rs.getString("config_key"), rs.getString("config_value"));
                }
                return configMap;
            });

        } catch (Exception e) {
            logger.error("Error loading all configuration values: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Find all configurations
     */
    public List<SystemConfiguration> findAll() {
        try {
            String sql = """
                SELECT id, config_key, config_value, config_type, description, category,
                       is_encrypted, is_readonly, default_value, created_at, updated_at,
                       created_by, updated_by
                FROM system_configuration 
                ORDER BY category, config_key
                """;
            
            return jdbcTemplate.query(sql, configurationRowMapper());

        } catch (Exception e) {
            logger.error("Error finding all configurations: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Update configuration value by key (UPSERT - insert if not exists)
     */
    public void updateConfigValue(String configKey, String newValue) {
        try {
            String currentUserId = com.integrixs.shared.util.AuditUtils.getCurrentUserId();
            
            // First try to update existing record
            String updateSql = """
                UPDATE system_configuration 
                SET config_value = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?::uuid
                WHERE config_key = ?
                """;
            
            int rowsUpdated = jdbcTemplate.update(updateSql, newValue, currentUserId, configKey);
            
            // If no rows were updated, insert new record
            if (rowsUpdated == 0) {
                String insertSql = """
                    INSERT INTO system_configuration (
                        id, config_key, config_value, config_type, description, category, default_value, is_readonly, 
                        created_at, created_by
                    ) 
                    VALUES (
                        gen_random_uuid(), ?, ?, 'STRING', ?, 'SYSTEM', ?, false,
                        CURRENT_TIMESTAMP, ?::uuid
                    )
                    """;
                
                String description = switch (configKey) {
                    case "system.environment.type" -> "Current environment type (DEVELOPMENT, TESTING, PRODUCTION)";
                    case "system.environment.enforce_restrictions" -> "Whether to enforce environment-based restrictions";
                    case "system.environment.restriction_message" -> "Message template for environment restriction warnings";
                    default -> "Auto-generated configuration value";
                };
                
                String defaultValue = switch (configKey) {
                    case "system.environment.type" -> "DEVELOPMENT";
                    case "system.environment.enforce_restrictions" -> "true";
                    case "system.environment.restriction_message" -> "This action is not allowed in %s environment";
                    default -> newValue;
                };
                
                jdbcTemplate.update(insertSql, configKey, newValue, description, defaultValue, currentUserId);
                logger.info("Inserted new configuration key '{}' with value", configKey);
            } else {
                logger.debug("Updated existing configuration key '{}' with new value", configKey);
            }

        } catch (Exception e) {
            logger.error("Error updating/inserting configuration key '{}': {}", configKey, e.getMessage());
            throw new RuntimeException("Failed to update configuration value", e);
        }
    }

    /**
     * Save or update configuration
     */
    public SystemConfiguration save(SystemConfiguration config) {
        if (findByKey(config.getConfigKey()).isPresent()) {
            return update(config);
        } else {
            return insert(config);
        }
    }

    /**
     * Insert new configuration
     */
    private SystemConfiguration insert(SystemConfiguration config) {
        try {
            // Set audit fields for INSERT
            config.setCreatedAt(LocalDateTime.now());
            // Don't set updated_at for new records
            
            String createdBy = AuditUtils.getCurrentUserId();
            
            String sql = """
                INSERT INTO system_configuration 
                (id, config_key, config_value, config_type, description, category,
                 is_encrypted, is_readonly, default_value, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            jdbcTemplate.update(sql,
                config.getId(),
                config.getConfigKey(),
                config.getConfigValue(),
                config.getConfigType().name(),
                config.getDescription(),
                config.getCategory().name(),
                config.isEncrypted(),
                config.isReadonly(),
                config.getDefaultValue(),
                config.getCreatedAt(),
                createdBy
            );
            
            logger.info("Inserted new configuration: {}", config.getConfigKey());
            
            // Log audit trail
            auditService.logDatabaseOperation("INSERT", "system_configuration", config.getId(), 
                config.getConfigKey(), true, null);
            
            return config;

        } catch (Exception e) {
            logger.error("Error inserting configuration '{}': {}", config.getConfigKey(), e.getMessage());
            
            // Log audit trail
            auditService.logDatabaseOperation("INSERT", "system_configuration", config.getId(), 
                config.getConfigKey(), true, null);
            
            throw new RuntimeException("Failed to save configuration", e);
        }
    }

    /**
     * Update existing configuration
     */
    private SystemConfiguration update(SystemConfiguration config) {
        try {
            String sql = """
                UPDATE system_configuration 
                SET config_value = ?, config_type = ?, description = ?, category = ?,
                    is_encrypted = ?, is_readonly = ?, default_value = ?, updated_by = ?, updated_at = ?
                WHERE config_key = ?
                """;
            
            config.setUpdatedAt(LocalDateTime.now());
            String updatedBy = AuditUtils.getCurrentUserId();
            
            int updated = jdbcTemplate.update(sql,
                config.getConfigValue(),
                config.getConfigType().name(),
                config.getDescription(),
                config.getCategory().name(),
                config.isEncrypted(),
                config.isReadonly(),
                config.getDefaultValue(),
                updatedBy,
                config.getUpdatedAt(),
                config.getConfigKey()
            );
            
            if (updated > 0) {
                logger.info("Updated configuration: {}", config.getConfigKey());
                
                // Log audit trail for configuration update
                auditService.logDatabaseOperation("UPDATE", "system_configuration", config.getId(), 
                    config.getConfigKey(), true, null);
                
                return config;
            } else {
                String errorMessage = "Configuration not found for update: " + config.getConfigKey();
                
                // Log failed audit trail
                auditService.logDatabaseOperation("UPDATE", "system_configuration", config.getId(), 
                    config.getConfigKey(), false, errorMessage);
                
                throw new RuntimeException(errorMessage);
            }

        } catch (Exception e) {
            logger.error("Error updating configuration '{}': {}", config.getConfigKey(), e.getMessage());
            
            // Log failed audit trail
            auditService.logDatabaseOperation("UPDATE", "system_configuration", config.getId(), 
                config.getConfigKey(), false, e.getMessage());
            
            throw new RuntimeException("Failed to update configuration", e);
        }
    }

    /**
     * Update configuration value only
     */
    public void updateValue(String configKey, String newValue, UUID updatedBy) {
        try {
            String sql = """
                UPDATE system_configuration 
                SET config_value = ?, updated_by = ?, updated_at = ?
                WHERE config_key = ?
                """;
            
            int updated = jdbcTemplate.update(sql, newValue, updatedBy, LocalDateTime.now(), configKey);
            
            if (updated > 0) {
                logger.info("Updated configuration value for key '{}' by user: {}", configKey, updatedBy);
            } else {
                logger.warn("No configuration found with key '{}' for value update", configKey);
            }

        } catch (Exception e) {
            logger.error("Error updating configuration value for key '{}': {}", configKey, e.getMessage());
            throw new RuntimeException("Failed to update configuration value", e);
        }
    }

    /**
     * Delete configuration (only if not readonly)
     */
    public boolean delete(String configKey) {
        try {
            // First check if it's readonly
            Optional<SystemConfiguration> config = findByKey(configKey);
            if (config.isPresent() && config.get().isReadonly()) {
                logger.warn("Attempted to delete readonly configuration: {}", configKey);
                return false;
            }

            String sql = "DELETE FROM system_configuration WHERE config_key = ? AND is_readonly = false";
            int deleted = jdbcTemplate.update(sql, configKey);
            
            if (deleted > 0) {
                logger.info("Deleted configuration: {}", configKey);
                return true;
            } else {
                logger.warn("No deletable configuration found with key: {}", configKey);
                return false;
            }

        } catch (Exception e) {
            logger.error("Error deleting configuration '{}': {}", configKey, e.getMessage());
            return false;
        }
    }

    /**
     * Get configurations by category as a map
     */
    public Map<String, String> getConfigurationsByCategory(String category) {
        try {
            String sql = """
                SELECT config_key, config_value
                FROM system_configuration 
                WHERE category = ?
                ORDER BY config_key
                """;
            
            return jdbcTemplate.query(sql, (rs) -> {
                Map<String, String> configMap = new java.util.HashMap<>();
                while (rs.next()) {
                    configMap.put(rs.getString("config_key"), rs.getString("config_value"));
                }
                return configMap;
            }, category);

        } catch (Exception e) {
            logger.error("Error loading configurations for category '{}': {}", category, e.getMessage());
            return Map.of();
        }
    }
    
    /**
     * Update configuration value only (without user tracking)
     */
    public void updateConfigurationValue(String configKey, String newValue) {
        try {
            String sql = """
                UPDATE system_configuration 
                SET config_value = ?, updated_at = CURRENT_TIMESTAMP
                WHERE config_key = ?
                """;
            
            int updated = jdbcTemplate.update(sql, newValue, configKey);
            
            if (updated > 0) {
                logger.info("Updated configuration value for key '{}'", configKey);
            } else {
                logger.warn("No configuration found with key '{}' for value update", configKey);
            }

        } catch (Exception e) {
            logger.error("Error updating configuration value for key '{}': {}", configKey, e.getMessage());
            throw new RuntimeException("Failed to update configuration value", e);
        }
    }

    /**
     * Get dashboard refresh intervals for frontend
     */
    public Map<String, Integer> getDashboardRefreshIntervals() {
        List<SystemConfiguration> dashboardConfigs = findByCategory(SystemConfiguration.ConfigCategory.DASHBOARD);
        
        return dashboardConfigs.stream()
                .filter(config -> config.getConfigKey().contains("interval"))
                .collect(Collectors.toMap(
                    SystemConfiguration::getConfigKey,
                    SystemConfiguration::getIntegerValue
                ));
    }

    /**
     * Row mapper for SystemConfiguration
     */
    private RowMapper<SystemConfiguration> configurationRowMapper() {
        return new RowMapper<SystemConfiguration>() {
            @Override
            public SystemConfiguration mapRow(ResultSet rs, int rowNum) throws SQLException {
                SystemConfiguration config = new SystemConfiguration();
                
                config.setId(UUID.fromString(rs.getString("id")));
                config.setConfigKey(rs.getString("config_key"));
                config.setConfigValue(rs.getString("config_value"));
                config.setConfigType(SystemConfiguration.ConfigType.valueOf(rs.getString("config_type")));
                config.setDescription(rs.getString("description"));
                config.setCategory(SystemConfiguration.ConfigCategory.valueOf(rs.getString("category")));
                config.setEncrypted(rs.getBoolean("is_encrypted"));
                config.setReadonly(rs.getBoolean("is_readonly"));
                config.setDefaultValue(rs.getString("default_value"));
                
                // Handle timestamps
                if (rs.getTimestamp("created_at") != null) {
                    config.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                }
                if (rs.getTimestamp("updated_at") != null) {
                    config.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
                }
                
                // Handle UUIDs
                String createdByStr = rs.getString("created_by");
                if (createdByStr != null) {
                    config.setCreatedBy(UUID.fromString(createdByStr));
                }
                String updatedByStr = rs.getString("updated_by");
                if (updatedByStr != null) {
                    config.setUpdatedBy(UUID.fromString(updatedByStr));
                }
                
                return config;
            }
        };
    }
}