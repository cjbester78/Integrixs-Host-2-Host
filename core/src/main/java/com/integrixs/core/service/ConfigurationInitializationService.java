package com.integrixs.core.service;

import com.integrixs.core.repository.SystemConfigurationRepository;
import com.integrixs.shared.model.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for initializing system configuration values in the database.
 * Seeds default configuration values that were previously hardcoded.
 * Follows OOP principles with proper encapsulation and immutable configuration definitions.
 */
@Service
public class ConfigurationInitializationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationInitializationService.class);
    
    private final SystemConfigurationRepository configRepository;
    
    @Autowired
    public ConfigurationInitializationService(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
    }
    
    /**
     * Immutable configuration definition
     */
    private static class ConfigurationDefinition {
        private final String key;
        private final String defaultValue;
        private final String description;
        private final SystemConfiguration.ConfigType configType;
        private final SystemConfiguration.ConfigCategory category;
        private final boolean readonly;
        private final String validationPattern;
        
        public ConfigurationDefinition(String key, String defaultValue, String description,
                                     SystemConfiguration.ConfigType configType, 
                                     SystemConfiguration.ConfigCategory category,
                                     boolean readonly, String validationPattern) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.description = description;
            this.configType = configType;
            this.category = category;
            this.readonly = readonly;
            this.validationPattern = validationPattern;
        }
        
        public String getKey() { return key; }
        public String getDefaultValue() { return defaultValue; }
        public String getDescription() { return description; }
        public SystemConfiguration.ConfigType getConfigType() { return configType; }
        public SystemConfiguration.ConfigCategory getCategory() { return category; }
        public boolean isReadonly() { return readonly; }
        public String getValidationPattern() { return validationPattern; }
    }
    
    /**
     * Initialize default configuration values
     */
    public void initializeDefaultConfigurations() {
        logger.info("Initializing system configuration values...");
        
        List<ConfigurationDefinition> defaultConfigs = getDefaultConfigurations();
        int initialized = 0;
        
        for (ConfigurationDefinition configDef : defaultConfigs) {
            try {
                Optional<SystemConfiguration> existingOpt = configRepository.findByKey(configDef.getKey());
                
                if (existingOpt.isEmpty()) {
                    // Create new configuration
                    SystemConfiguration newConfig = createConfiguration(configDef);
                    configRepository.save(newConfig);
                    initialized++;
                    logger.debug("Initialized configuration: {}", configDef.getKey());
                } else {
                    // Configuration already exists, skip update for now
                    logger.debug("Configuration already exists: {}", configDef.getKey());
                }
                
            } catch (Exception e) {
                logger.error("Error initializing configuration {}: {}", configDef.getKey(), e.getMessage());
            }
        }
        
        logger.info("Configuration initialization completed: {} new configurations", initialized);
    }
    
    /**
     * Create system configuration from definition
     */
    private SystemConfiguration createConfiguration(ConfigurationDefinition configDef) {
        SystemConfiguration config = new SystemConfiguration();
        config.setConfigKey(configDef.getKey());
        config.setConfigValue(configDef.getDefaultValue());
        config.setDefaultValue(configDef.getDefaultValue());
        config.setDescription(configDef.getDescription());
        config.setConfigType(configDef.getConfigType());
        config.setCategory(configDef.getCategory());
        config.setReadonly(configDef.isReadonly());
        // Note: Some properties like validationPattern and active may not exist in this model
        return config;
    }
    
    /**
     * Get default configuration definitions
     */
    private List<ConfigurationDefinition> getDefaultConfigurations() {
        List<ConfigurationDefinition> configs = new ArrayList<>();
        
        // Configuration validation settings
        configs.add(new ConfigurationDefinition(
            "config.validation.max-string-length", "1000",
            "Maximum allowed length for string configuration values",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.validation.max-integer-value", "999999999",
            "Maximum allowed value for integer configurations",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.validation.min-integer-value", "-999999999",
            "Minimum allowed value for integer configurations",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.validation.max-timeout-seconds", "3600",
            "Maximum allowed timeout value in seconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.validation.min-timeout-seconds", "1",
            "Minimum allowed timeout value in seconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, null
        ));
        
        // Configuration security settings
        configs.add(new ConfigurationDefinition(
            "config.security.require-admin-for-sensitive", "true",
            "Require administrative privileges for sensitive configuration changes",
            SystemConfiguration.ConfigType.BOOLEAN,
            SystemConfiguration.ConfigCategory.SECURITY,
            false, "true,false"
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.security.audit-all-changes", "true",
            "Enable auditing of all configuration changes",
            SystemConfiguration.ConfigType.BOOLEAN,
            SystemConfiguration.ConfigCategory.SECURITY,
            false, "true,false"
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.security.max-failed-attempts", "5",
            "Maximum failed configuration change attempts before lockout",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SECURITY,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.security.lockout-duration-minutes", "30",
            "User lockout duration in minutes after max failed attempts",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SECURITY,
            false, null
        ));
        
        // Flow execution settings
        configs.add(new ConfigurationDefinition(
            "flow.parallel.execution.timeout.seconds", "30",
            "Timeout for parallel flow execution in seconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "flow.parallel.execution.timeout.min-seconds", "5",
            "Minimum allowed parallel execution timeout in seconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            true, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "flow.parallel.execution.timeout.max-seconds", "300",
            "Maximum allowed parallel execution timeout in seconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            true, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "flow.context.max-snapshot-size", "10000",
            "Maximum size of execution context snapshots",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "flow.context.cleanup-after-completion", "true",
            "Clean up execution context after flow completion",
            SystemConfiguration.ConfigType.BOOLEAN,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, "true,false"
        ));
        
        // Configuration hierarchy settings
        configs.add(new ConfigurationDefinition(
            "config.hierarchy.enable-environment-overrides", "true",
            "Allow environment variables to override configuration values",
            SystemConfiguration.ConfigType.BOOLEAN,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, "true,false"
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.hierarchy.enable-system-property-overrides", "true",
            "Allow system properties to override configuration values",
            SystemConfiguration.ConfigType.BOOLEAN,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, "true,false"
        ));
        
        configs.add(new ConfigurationDefinition(
            "config.hierarchy.cache-resolved-values", "true",
            "Cache resolved configuration values for performance",
            SystemConfiguration.ConfigType.BOOLEAN,
            SystemConfiguration.ConfigCategory.SYSTEM,
            false, "true,false"
        ));
        
        // Dashboard refresh intervals (existing)
        configs.add(new ConfigurationDefinition(
            "dashboard.refresh.health_interval", "30000",
            "Health status refresh interval in milliseconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.DASHBOARD,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "dashboard.refresh.adapter_stats_interval", "60000",
            "Adapter statistics refresh interval in milliseconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.DASHBOARD,
            false, null
        ));
        
        configs.add(new ConfigurationDefinition(
            "dashboard.refresh.recent_executions_interval", "60000",
            "Recent executions refresh interval in milliseconds",
            SystemConfiguration.ConfigType.INTEGER,
            SystemConfiguration.ConfigCategory.DASHBOARD,
            false, null
        ));
        
        return configs;
    }
}