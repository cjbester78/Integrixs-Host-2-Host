package com.integrixs.backend.service;

import com.integrixs.shared.model.SystemConfiguration;
import com.integrixs.core.repository.SystemConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing system configuration settings
 * Provides caching and validation for configuration operations
 */
@Service
@Transactional
public class SystemConfigurationService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigurationService.class);

    private final SystemConfigurationRepository configRepository;

    public SystemConfigurationService(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Get configuration value with caching
     */
    @Cacheable(value = "systemConfig", key = "#configKey")
    public String getValue(String configKey, String defaultValue) {
        try {
            return configRepository.getValue(configKey, defaultValue);
        } catch (Exception e) {
            logger.error("Error getting configuration value for key '{}': {}", configKey, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Alias for getValue - for compatibility with environment config
     */
    public String getConfigValue(String configKey, String defaultValue) {
        return getValue(configKey, defaultValue);
    }

    /**
     * Update configuration value and clear cache
     */
    @CacheEvict(value = {"systemConfig", "systemConfigInt", "systemConfigBool"}, key = "#configKey")
    public void updateConfigValue(String configKey, String newValue) {
        try {
            configRepository.updateConfigValue(configKey, newValue);
            logger.info("Updated configuration key '{}' to new value", configKey);
        } catch (Exception e) {
            logger.error("Error updating configuration key '{}': {}", configKey, e.getMessage());
            throw new RuntimeException("Failed to update configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Get integer configuration value with caching
     */
    @Cacheable(value = "systemConfigInt", key = "#configKey")
    public Integer getIntegerValue(String configKey, Integer defaultValue) {
        try {
            return configRepository.getIntegerValue(configKey, defaultValue);
        } catch (Exception e) {
            logger.error("Error getting integer configuration value for key '{}': {}", configKey, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Get boolean configuration value with caching
     */
    @Cacheable(value = "systemConfigBool", key = "#configKey")
    public Boolean getBooleanValue(String configKey, Boolean defaultValue) {
        try {
            return configRepository.getBooleanValue(configKey, defaultValue);
        } catch (Exception e) {
            logger.error("Error getting boolean configuration value for key '{}': {}", configKey, e.getMessage());
            return defaultValue;
        }
    }

    /**
     * Get dashboard refresh intervals for frontend
     */
    @Cacheable(value = "dashboardIntervals")
    public Map<String, Integer> getDashboardRefreshIntervals() {
        try {
            Map<String, Integer> intervals = configRepository.getDashboardRefreshIntervals();
            
            // Ensure we have default values if database is empty
            intervals.putIfAbsent("dashboard.refresh.health_interval", 30000);
            intervals.putIfAbsent("dashboard.refresh.adapter_stats_interval", 60000);
            intervals.putIfAbsent("dashboard.refresh.recent_executions_interval", 60000);
            
            logger.debug("Retrieved dashboard refresh intervals: {}", intervals);
            return intervals;
            
        } catch (Exception e) {
            logger.error("Error getting dashboard refresh intervals: {}", e.getMessage());
            // Return sensible defaults
            return Map.of(
                "dashboard.refresh.health_interval", 30000,
                "dashboard.refresh.adapter_stats_interval", 60000,
                "dashboard.refresh.recent_executions_interval", 60000
            );
        }
    }

    /**
     * Find all configurations by category
     */
    public List<SystemConfiguration> getConfigurationsByCategory(SystemConfiguration.ConfigCategory category) {
        try {
            return configRepository.findByCategory(category);
        } catch (Exception e) {
            logger.error("Error getting configurations by category '{}': {}", category, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get all configurations
     */
    public List<SystemConfiguration> getAllConfigurations() {
        try {
            return configRepository.findAll();
        } catch (Exception e) {
            logger.error("Error getting all configurations: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Find configuration by key
     */
    public Optional<SystemConfiguration> getConfigurationByKey(String configKey) {
        try {
            return configRepository.findByKey(configKey);
        } catch (Exception e) {
            logger.error("Error finding configuration by key '{}': {}", configKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Update configuration value with validation and cache eviction
     */
    @CacheEvict(value = {"systemConfig", "systemConfigInt", "systemConfigBool", "dashboardIntervals"}, allEntries = true)
    public void updateConfigurationValue(String configKey, String newValue, UUID updatedBy) {
        try {
            // Validate the configuration exists and the value is valid
            Optional<SystemConfiguration> configOpt = configRepository.findByKey(configKey);
            if (configOpt.isEmpty()) {
                throw new IllegalArgumentException("Configuration key not found: " + configKey);
            }

            SystemConfiguration config = configOpt.get();
            
            // Check if readonly
            if (config.isReadonly()) {
                throw new IllegalStateException("Configuration '" + configKey + "' is readonly and cannot be modified");
            }

            // Validate the new value
            String validationError = config.getValidationError(newValue);
            if (validationError != null) {
                throw new IllegalArgumentException("Invalid value for '" + configKey + "': " + validationError);
            }

            // Update the value
            configRepository.updateValue(configKey, newValue, updatedBy);
            
            logger.info("Updated configuration '{}' to value '{}' by user: {}", configKey, newValue, updatedBy);

        } catch (Exception e) {
            logger.error("Error updating configuration value for key '{}': {}", configKey, e.getMessage());
            throw e;
        }
    }

    /**
     * Save or update configuration with validation
     */
    @CacheEvict(value = {"systemConfig", "systemConfigInt", "systemConfigBool", "dashboardIntervals"}, allEntries = true)
    public SystemConfiguration saveConfiguration(SystemConfiguration config, UUID userId) {
        try {
            // Validate the configuration
            if (config.getConfigKey() == null || config.getConfigKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Configuration key cannot be empty");
            }
            
            if (config.getConfigValue() == null) {
                throw new IllegalArgumentException("Configuration value cannot be null");
            }

            // Validate the value according to type
            String validationError = config.getValidationError(config.getConfigValue());
            if (validationError != null) {
                throw new IllegalArgumentException("Invalid configuration value: " + validationError);
            }

            // Set audit fields
            config.setUpdatedBy(userId);
            if (config.getCreatedBy() == null) {
                config.setCreatedBy(userId);
            }

            SystemConfiguration saved = configRepository.save(config);
            
            logger.info("Saved configuration '{}' by user: {}", config.getConfigKey(), userId);
            return saved;

        } catch (Exception e) {
            logger.error("Error saving configuration '{}': {}", config.getConfigKey(), e.getMessage());
            throw e;
        }
    }

    /**
     * Delete configuration if not readonly
     */
    @CacheEvict(value = {"systemConfig", "systemConfigInt", "systemConfigBool", "dashboardIntervals"}, allEntries = true)
    public boolean deleteConfiguration(String configKey) {
        try {
            boolean deleted = configRepository.delete(configKey);
            if (deleted) {
                logger.info("Deleted configuration: {}", configKey);
            } else {
                logger.warn("Failed to delete configuration (may be readonly): {}", configKey);
            }
            return deleted;

        } catch (Exception e) {
            logger.error("Error deleting configuration '{}': {}", configKey, e.getMessage());
            return false;
        }
    }

    /**
     * Reset configuration to default value
     */
    @CacheEvict(value = {"systemConfig", "systemConfigInt", "systemConfigBool", "dashboardIntervals"}, allEntries = true)
    public void resetToDefault(String configKey, UUID userId) {
        try {
            Optional<SystemConfiguration> configOpt = configRepository.findByKey(configKey);
            if (configOpt.isEmpty()) {
                throw new IllegalArgumentException("Configuration key not found: " + configKey);
            }

            SystemConfiguration config = configOpt.get();
            
            if (config.isReadonly()) {
                throw new IllegalStateException("Configuration '" + configKey + "' is readonly and cannot be reset");
            }

            String defaultValue = config.getDefaultValue();
            if (defaultValue == null) {
                throw new IllegalStateException("Configuration '" + configKey + "' has no default value");
            }

            configRepository.updateValue(configKey, defaultValue, userId);
            
            logger.info("Reset configuration '{}' to default value '{}' by user: {}", 
                       configKey, defaultValue, userId);

        } catch (Exception e) {
            logger.error("Error resetting configuration '{}' to default: {}", configKey, e.getMessage());
            throw e;
        }
    }

    /**
     * Clear all configuration caches
     */
    @CacheEvict(value = {"systemConfig", "systemConfigInt", "systemConfigBool", "dashboardIntervals"}, allEntries = true)
    public void clearCache() {
        logger.info("Cleared all system configuration caches");
    }

    /**
     * Get configuration statistics
     */
    public Map<String, Object> getConfigurationStatistics() {
        try {
            List<SystemConfiguration> allConfigs = configRepository.findAll();
            
            Map<SystemConfiguration.ConfigCategory, Long> byCategory = allConfigs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    SystemConfiguration::getCategory,
                    java.util.stream.Collectors.counting()
                ));

            long readonlyCount = allConfigs.stream()
                .mapToLong(config -> config.isReadonly() ? 1 : 0)
                .sum();

            return Map.of(
                "totalConfigurations", allConfigs.size(),
                "readonlyConfigurations", readonlyCount,
                "editableConfigurations", allConfigs.size() - readonlyCount,
                "configurationsByCategory", byCategory
            );

        } catch (Exception e) {
            logger.error("Error getting configuration statistics: {}", e.getMessage());
            return Map.of("error", "Failed to load statistics");
        }
    }
}