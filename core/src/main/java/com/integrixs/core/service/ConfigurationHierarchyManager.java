package com.integrixs.core.service;

import com.integrixs.core.repository.SystemConfigurationRepository;
import com.integrixs.shared.model.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing configuration hierarchy and inheritance patterns.
 * Implements proper configuration resolution with environment overrides.
 * Follows OOP principles with immutable result objects and clear separation of concerns.
 */
@Service
public class ConfigurationHierarchyManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationHierarchyManager.class);
    
    private final SystemConfigurationRepository configRepository;
    private final Environment environment;
    
    @Autowired
    public ConfigurationHierarchyManager(SystemConfigurationRepository configRepository, 
                                       Environment environment) {
        this.configRepository = configRepository;
        this.environment = environment;
    }
    
    /**
     * Immutable configuration resolution result
     */
    public static class ConfigurationResolutionResult {
        private final String resolvedValue;
        private final ConfigurationSource source;
        private final List<ConfigurationSource> searchOrder;
        private final boolean overridden;
        private final String originalValue;
        
        public ConfigurationResolutionResult(String resolvedValue, ConfigurationSource source,
                                           List<ConfigurationSource> searchOrder, boolean overridden, 
                                           String originalValue) {
            this.resolvedValue = resolvedValue;
            this.source = source;
            this.searchOrder = new ArrayList<>(searchOrder);
            this.overridden = overridden;
            this.originalValue = originalValue;
        }
        
        public String getResolvedValue() { return resolvedValue; }
        public ConfigurationSource getSource() { return source; }
        public List<ConfigurationSource> getSearchOrder() { return new ArrayList<>(searchOrder); }
        public boolean isOverridden() { return overridden; }
        public String getOriginalValue() { return originalValue; }
    }
    
    /**
     * Configuration source enumeration
     */
    public enum ConfigurationSource {
        ENVIRONMENT_VARIABLE("Environment Variable", 1),
        SYSTEM_PROPERTY("System Property", 2),
        APPLICATION_YAML("Application YAML", 3),
        DATABASE("Database", 4),
        DEFAULT_VALUE("Default Value", 5);
        
        private final String displayName;
        private final int priority;
        
        ConfigurationSource(String displayName, int priority) {
            this.displayName = displayName;
            this.priority = priority;
        }
        
        public String getDisplayName() { return displayName; }
        public int getPriority() { return priority; }
    }
    
    /**
     * Resolve configuration value using hierarchy
     */
    public ConfigurationResolutionResult resolveConfiguration(String configKey, String defaultValue) {
        List<ConfigurationSource> searchOrder = getSearchOrder();
        String originalValue = null;
        boolean overridden = false;
        
        for (ConfigurationSource source : searchOrder) {
            Optional<String> value = getValueFromSource(configKey, source);
            if (value.isPresent()) {
                String resolvedValue = value.get();
                
                // Track if this value overrides a lower priority source
                if (originalValue != null && !originalValue.equals(resolvedValue)) {
                    overridden = true;
                }
                if (originalValue == null) {
                    originalValue = resolvedValue;
                }
                
                return new ConfigurationResolutionResult(resolvedValue, source, searchOrder, 
                                                       overridden, originalValue);
            }
        }
        
        // No value found, use default
        return new ConfigurationResolutionResult(defaultValue, ConfigurationSource.DEFAULT_VALUE, 
                                                searchOrder, false, defaultValue);
    }
    
    /**
     * Get configuration search order based on priority
     */
    private List<ConfigurationSource> getSearchOrder() {
        List<ConfigurationSource> order = new ArrayList<>();
        order.add(ConfigurationSource.ENVIRONMENT_VARIABLE);
        order.add(ConfigurationSource.SYSTEM_PROPERTY);
        order.add(ConfigurationSource.APPLICATION_YAML);
        order.add(ConfigurationSource.DATABASE);
        return order;
    }
    
    /**
     * Get value from specific configuration source
     */
    private Optional<String> getValueFromSource(String configKey, ConfigurationSource source) {
        try {
            switch (source) {
                case ENVIRONMENT_VARIABLE:
                    return getFromEnvironmentVariable(configKey);
                case SYSTEM_PROPERTY:
                    return getFromSystemProperty(configKey);
                case APPLICATION_YAML:
                    return getFromApplicationYaml(configKey);
                case DATABASE:
                    return getFromDatabase(configKey);
                default:
                    return Optional.empty();
            }
        } catch (Exception e) {
            logger.debug("Error getting value from {}: {}", source.getDisplayName(), e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Get value from environment variable
     */
    private Optional<String> getFromEnvironmentVariable(String configKey) {
        // Convert dot notation to underscore notation for environment variables
        String envKey = configKey.toUpperCase().replace('.', '_').replace('-', '_');
        String value = System.getenv(envKey);
        
        if (value != null && !value.trim().isEmpty()) {
            logger.debug("Found configuration {} in environment variable {}", configKey, envKey);
            return Optional.of(value.trim());
        }
        
        return Optional.empty();
    }
    
    /**
     * Get value from system property
     */
    private Optional<String> getFromSystemProperty(String configKey) {
        String value = System.getProperty(configKey);
        
        if (value != null && !value.trim().isEmpty()) {
            logger.debug("Found configuration {} in system property", configKey);
            return Optional.of(value.trim());
        }
        
        return Optional.empty();
    }
    
    /**
     * Get value from Spring Environment (application.yml)
     */
    private Optional<String> getFromApplicationYaml(String configKey) {
        try {
            String value = environment.getProperty(configKey);
            
            if (value != null && !value.trim().isEmpty()) {
                logger.debug("Found configuration {} in application.yml", configKey);
                return Optional.of(value.trim());
            }
        } catch (Exception e) {
            logger.debug("Error reading from application.yml: {}", e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Get value from database
     */
    private Optional<String> getFromDatabase(String configKey) {
        try {
            Optional<SystemConfiguration> configOpt = configRepository.findByKey(configKey);
            if (configOpt.isPresent()) {
                SystemConfiguration config = configOpt.get();
                String value = config.getConfigValue();
                
                if (value != null && !value.trim().isEmpty()) {
                    logger.debug("Found configuration {} in database", configKey);
                    return Optional.of(value.trim());
                }
            }
        } catch (Exception e) {
            logger.debug("Error reading from database: {}", e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Get all configuration overrides for a given prefix
     */
    public Map<String, ConfigurationResolutionResult> getConfigurationOverrides(String prefix) {
        Map<String, ConfigurationResolutionResult> overrides = new HashMap<>();
        
        try {
            // Get all database configurations and filter by prefix
            List<SystemConfiguration> allConfigs = configRepository.findAll();
            
            for (SystemConfiguration config : allConfigs) {
                if (config.getConfigKey().startsWith(prefix)) {
                    ConfigurationResolutionResult result = resolveConfiguration(config.getConfigKey(), 
                                                                               config.getDefaultValue());
                    if (result.isOverridden()) {
                        overrides.put(config.getConfigKey(), result);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error getting configuration overrides for prefix {}: {}", prefix, e.getMessage());
        }
        
        return overrides;
    }
    
    /**
     * Get effective configuration values for a category
     */
    public Map<String, ConfigurationResolutionResult> getEffectiveConfigurations(
            SystemConfiguration.ConfigCategory category) {
        
        Map<String, ConfigurationResolutionResult> effective = new HashMap<>();
        
        try {
            List<SystemConfiguration> configs = configRepository.findByCategory(category);
            
            for (SystemConfiguration config : configs) {
                ConfigurationResolutionResult result = resolveConfiguration(config.getConfigKey(), 
                                                                           config.getDefaultValue());
                effective.put(config.getConfigKey(), result);
            }
            
        } catch (Exception e) {
            logger.error("Error getting effective configurations for category {}: {}", 
                        category, e.getMessage());
        }
        
        return effective;
    }
    
    /**
     * Check if configuration value is overridden by higher priority source
     */
    public boolean isConfigurationOverridden(String configKey) {
        ConfigurationResolutionResult result = resolveConfiguration(configKey, null);
        return result.isOverridden();
    }
    
    /**
     * Get configuration source priority information
     */
    public Map<String, Object> getSourcePriorityInfo() {
        Map<String, Object> info = new HashMap<>();
        
        List<Map<String, Object>> sources = new ArrayList<>();
        for (ConfigurationSource source : getSearchOrder()) {
            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("name", source.getDisplayName());
            sourceInfo.put("priority", source.getPriority());
            sourceInfo.put("description", getSourceDescription(source));
            sources.add(sourceInfo);
        }
        
        info.put("searchOrder", sources);
        info.put("description", "Configuration values are resolved in priority order (1 = highest priority)");
        
        return info;
    }
    
    /**
     * Get source description for documentation
     */
    private String getSourceDescription(ConfigurationSource source) {
        switch (source) {
            case ENVIRONMENT_VARIABLE:
                return "Environment variables (e.g., FLOW_TIMEOUT_SECONDS)";
            case SYSTEM_PROPERTY:
                return "JVM system properties (e.g., -Dflow.timeout.seconds=30)";
            case APPLICATION_YAML:
                return "Spring application.yml configuration files";
            case DATABASE:
                return "Database-stored system configuration values";
            case DEFAULT_VALUE:
                return "Default values specified in configuration definitions";
            default:
                return "Unknown source";
        }
    }
    
    /**
     * Validate configuration hierarchy consistency
     */
    public List<String> validateHierarchyConsistency() {
        List<String> issues = new ArrayList<>();
        
        try {
            List<SystemConfiguration> allConfigs = configRepository.findAll();
            
            for (SystemConfiguration config : allConfigs) {
                ConfigurationResolutionResult result = resolveConfiguration(config.getConfigKey(), 
                                                                           config.getDefaultValue());
                
                // Check if database value differs significantly from resolved value
                if (result.getSource() != ConfigurationSource.DATABASE && 
                    config.getConfigValue() != null && 
                    !config.getConfigValue().equals(result.getResolvedValue())) {
                    
                    issues.add("Configuration '" + config.getConfigKey() + 
                             "' has database value '" + config.getConfigValue() + 
                             "' but resolves to '" + result.getResolvedValue() + 
                             "' from " + result.getSource().getDisplayName());
                }
            }
            
        } catch (Exception e) {
            issues.add("Error validating hierarchy consistency: " + e.getMessage());
        }
        
        return issues;
    }
}