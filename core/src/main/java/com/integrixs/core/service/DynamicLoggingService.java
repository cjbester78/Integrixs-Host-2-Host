package com.integrixs.core.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.integrixs.core.repository.SystemConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for dynamically configuring logging levels and settings based on database configuration
 * This allows runtime modification of logging behavior without application restart
 */
@Service
public class DynamicLoggingService {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicLoggingService.class);
    private final SystemConfigurationRepository configRepository;
    private final LoggerContext loggerContext;
    
    // Cache for current logging configuration to avoid unnecessary database calls
    private final Map<String, String> loggingConfigCache = new HashMap<>();
    private long lastConfigUpdate = 0;
    private static final long CONFIG_CACHE_DURATION_MS = 60000; // 1 minute cache
    
    @Autowired
    public DynamicLoggingService(SystemConfigurationRepository configRepository) {
        this.configRepository = configRepository;
        this.loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
    }
    
    /**
     * Initialize logging configuration from database on startup
     */
    public void initializeLoggingConfiguration() {
        try {
            logger.info("Initializing dynamic logging configuration from database");
            
            // Load all logging configuration from database
            Map<String, String> loggingConfig = loadLoggingConfigFromDatabase();
            
            // Apply configuration to loggers
            applyLoggingConfiguration(loggingConfig);
            
            // Cache the configuration
            loggingConfigCache.clear();
            loggingConfigCache.putAll(loggingConfig);
            lastConfigUpdate = System.currentTimeMillis();
            
            logger.info("Dynamic logging configuration initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize dynamic logging configuration", e);
        }
    }
    
    /**
     * Reload configuration from database and apply changes
     */
    public void reloadConfiguration() {
        try {
            logger.info("Reloading logging configuration from database");
            
            Map<String, String> newConfig = loadLoggingConfigFromDatabase();
            Map<String, String> changes = detectChanges(loggingConfigCache, newConfig);
            
            if (!changes.isEmpty()) {
                logger.info("Detected {} logging configuration changes", changes.size());
                applyLoggingConfiguration(newConfig);
                
                // Update cache
                loggingConfigCache.clear();
                loggingConfigCache.putAll(newConfig);
                lastConfigUpdate = System.currentTimeMillis();
                
                logger.info("Logging configuration reloaded successfully");
            } else {
                logger.debug("No logging configuration changes detected");
            }
            
        } catch (Exception e) {
            logger.error("Failed to reload logging configuration", e);
        }
    }
    
    /**
     * Set logging level for a specific logger
     */
    public void setLoggerLevel(String loggerName, String level) {
        try {
            Level logbackLevel = Level.valueOf(level.toUpperCase());
            ch.qos.logback.classic.Logger targetLogger = loggerContext.getLogger(loggerName);
            
            Level oldLevel = targetLogger.getLevel();
            targetLogger.setLevel(logbackLevel);
            
            logger.info("Changed logger '{}' level from {} to {}", loggerName, oldLevel, logbackLevel);
            
            // Update database configuration
            String configKey = getLoggerConfigKey(loggerName);
            configRepository.updateConfigurationValue(configKey, level.toUpperCase());
            
            // Update cache
            loggingConfigCache.put(configKey, level.toUpperCase());
            
        } catch (Exception e) {
            logger.error("Failed to set logger level for '{}' to '{}'", loggerName, level, e);
            throw new RuntimeException("Failed to update logger level: " + e.getMessage());
        }
    }
    
    /**
     * Get current effective logging level for a logger
     */
    public String getLoggerLevel(String loggerName) {
        ch.qos.logback.classic.Logger targetLogger = loggerContext.getLogger(loggerName);
        Level effectiveLevel = targetLogger.getEffectiveLevel();
        return effectiveLevel != null ? effectiveLevel.toString() : "INHERITED";
    }
    
    /**
     * Get all current logging configuration
     */
    public Map<String, String> getCurrentLoggingConfiguration() {
        // Check if cache is still valid
        if (System.currentTimeMillis() - lastConfigUpdate > CONFIG_CACHE_DURATION_MS) {
            try {
                Map<String, String> freshConfig = loadLoggingConfigFromDatabase();
                loggingConfigCache.clear();
                loggingConfigCache.putAll(freshConfig);
                lastConfigUpdate = System.currentTimeMillis();
            } catch (Exception e) {
                logger.warn("Failed to refresh logging configuration cache", e);
            }
        }
        
        return new HashMap<>(loggingConfigCache);
    }
    
    /**
     * Load logging configuration from database
     */
    private Map<String, String> loadLoggingConfigFromDatabase() {
        Map<String, String> config = new HashMap<>();
        
        try {
            // Load all LOGGING category configurations
            Map<String, String> loggingConfigs = configRepository.getConfigurationsByCategory("LOGGING");
            
            for (Map.Entry<String, String> entry : loggingConfigs.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                if (value != null && !value.trim().isEmpty()) {
                    config.put(key, value);
                }
            }
            
            logger.debug("Loaded {} logging configuration entries from database", config.size());
            
        } catch (Exception e) {
            logger.error("Failed to load logging configuration from database", e);
        }
        
        return config;
    }
    
    /**
     * Apply logging configuration to loggers
     */
    private void applyLoggingConfiguration(Map<String, String> config) {
        try {
            // Apply root logger level
            String rootLevel = config.get("logging.level.root");
            if (rootLevel != null) {
                ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
                rootLogger.setLevel(Level.valueOf(rootLevel.toUpperCase()));
                logger.debug("Set root logger level to {}", rootLevel);
            }
            
            // Apply category-specific levels
            applyLoggerLevel(config, "logging.level.system", "com.integrixs");
            applyLoggerLevel(config, "logging.level.adapter_execution", "com.integrixs.adapters");
            applyLoggerLevel(config, "logging.level.flow_execution", "com.integrixs.flows");
            applyLoggerLevel(config, "logging.level.authentication", "com.integrixs.backend.security");
            applyLoggerLevel(config, "logging.level.database", "com.integrixs.core.repository");
            applyLoggerLevel(config, "logging.level.api", "com.integrixs.backend.controller");
            applyLoggerLevel(config, "logging.level.scheduler", "com.integrixs.backend.scheduler");
            
            // Apply framework levels to reduce noise
            applyLoggerLevel(config, "logging.framework.spring_level", "org.springframework");
            applyLoggerLevel(config, "logging.framework.hibernate_level", "org.hibernate");
            applyLoggerLevel(config, "logging.framework.apache_level", "org.apache");
            
            logger.debug("Applied logging configuration to all loggers");
            
        } catch (Exception e) {
            logger.error("Failed to apply logging configuration", e);
        }
    }
    
    /**
     * Apply a specific logger level from configuration
     */
    private void applyLoggerLevel(Map<String, String> config, String configKey, String loggerName) {
        String levelValue = config.get(configKey);
        if (levelValue != null) {
            try {
                Level level = Level.valueOf(levelValue.toUpperCase());
                ch.qos.logback.classic.Logger targetLogger = loggerContext.getLogger(loggerName);
                targetLogger.setLevel(level);
                logger.debug("Set logger '{}' to level {}", loggerName, level);
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid log level '{}' for config key '{}'", levelValue, configKey);
            }
        }
    }
    
    /**
     * Detect changes between old and new configuration
     */
    private Map<String, String> detectChanges(Map<String, String> oldConfig, Map<String, String> newConfig) {
        Map<String, String> changes = new HashMap<>();
        
        // Check for changed or new values
        for (Map.Entry<String, String> entry : newConfig.entrySet()) {
            String key = entry.getKey();
            String newValue = entry.getValue();
            String oldValue = oldConfig.get(key);
            
            if (!newValue.equals(oldValue)) {
                changes.put(key, newValue);
            }
        }
        
        // Check for removed values
        for (String key : oldConfig.keySet()) {
            if (!newConfig.containsKey(key)) {
                changes.put(key, null); // Indicates removal
            }
        }
        
        return changes;
    }
    
    /**
     * Convert logger name to configuration key
     */
    private String getLoggerConfigKey(String loggerName) {
        if ("ROOT".equals(loggerName) || Logger.ROOT_LOGGER_NAME.equals(loggerName)) {
            return "logging.level.root";
        }
        
        // Map specific loggers to configuration keys
        if (loggerName.startsWith("com.integrixs.adapters")) {
            return "logging.level.adapter_execution";
        }
        if (loggerName.startsWith("com.integrixs.flows")) {
            return "logging.level.flow_execution";
        }
        if (loggerName.startsWith("com.integrixs.backend.security")) {
            return "logging.level.authentication";
        }
        if (loggerName.startsWith("com.integrixs.core.repository")) {
            return "logging.level.database";
        }
        if (loggerName.startsWith("com.integrixs.backend.controller")) {
            return "logging.level.api";
        }
        if (loggerName.startsWith("com.integrixs.backend.scheduler")) {
            return "logging.level.scheduler";
        }
        if (loggerName.startsWith("com.integrixs")) {
            return "logging.level.system";
        }
        if (loggerName.startsWith("org.springframework")) {
            return "logging.framework.spring_level";
        }
        if (loggerName.startsWith("org.hibernate")) {
            return "logging.framework.hibernate_level";
        }
        if (loggerName.startsWith("org.apache")) {
            return "logging.framework.apache_level";
        }
        
        return "logging.level.custom." + loggerName;
    }
    
    /**
     * Get a summary of current logging configuration for monitoring
     */
    public Map<String, Object> getLoggingConfigurationSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        try {
            Map<String, String> config = getCurrentLoggingConfiguration();
            
            summary.put("totalConfigurations", config.size());
            summary.put("lastUpdate", lastConfigUpdate);
            summary.put("cacheAge", System.currentTimeMillis() - lastConfigUpdate);
            
            // Count by category
            Map<String, Integer> categoryCounts = new HashMap<>();
            for (String key : config.keySet()) {
                String category = key.split("\\.")[1]; // Extract second part (level, file, etc.)
                categoryCounts.merge(category, 1, Integer::sum);
            }
            summary.put("configurationsByCategory", categoryCounts);
            
            // Current levels for main loggers
            Map<String, String> currentLevels = new HashMap<>();
            currentLevels.put("root", getLoggerLevel(Logger.ROOT_LOGGER_NAME));
            currentLevels.put("application", getLoggerLevel("com.integrixs"));
            currentLevels.put("spring", getLoggerLevel("org.springframework"));
            currentLevels.put("adapters", getLoggerLevel("com.integrixs.adapters"));
            summary.put("currentLevels", currentLevels);
            
        } catch (Exception e) {
            summary.put("error", e.getMessage());
        }
        
        return summary;
    }
}