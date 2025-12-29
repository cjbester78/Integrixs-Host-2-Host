package com.integrixs.backend.service;

import com.integrixs.core.config.ConfigurationManager;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.shared.constants.H2HConstants;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Service for configuration management
 */
@Service
public class ConfigurationService {

    private static final EnhancedLogger logger = EnhancedLogger.getLogger(ConfigurationService.class);
    
    private final ConfigurationManager configManager;

    public ConfigurationService() {
        this.configManager = new ConfigurationManager();
    }

    /**
     * Get configuration for a specific bank and operation type
     */
    public Map<String, String> getBankConfiguration(String bankName, String operationType, String direction) 
            throws IOException {
        
        String configFileName = buildConfigFileName(bankName, operationType, direction);
        Properties props;
        try {
            props = configManager.loadConfiguration(configFileName);
        } catch (Exception e) {
            throw new IOException("Failed to load configuration: " + configFileName, e);
        }
        
        Map<String, String> config = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            config.put(key, props.getProperty(key));
        }
        
        return config;
    }

    /**
     * Get application configuration
     */
    public Map<String, String> getApplicationConfiguration() throws IOException {
        Properties props;
        try {
            props = configManager.loadConfiguration(H2HConstants.APP_CONFIG_FILE);
        } catch (Exception e) {
            throw new IOException("Failed to load application configuration", e);
        }
        
        Map<String, String> config = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            config.put(key, props.getProperty(key));
        }
        
        return config;
    }

    /**
     * Update configuration for a specific bank and operation type
     */
    public void updateBankConfiguration(String bankName, String operationType, String direction, 
                                      Map<String, String> configuration) throws IOException {
        
        String configFileName = buildConfigFileName(bankName, operationType, direction);
        Properties props = new Properties();
        
        // Load existing configuration first
        try {
            props = configManager.loadConfiguration(configFileName);
        } catch (Exception e) {
            logger.info("Creating new configuration file: {}", configFileName);
        }
        
        // Update with new values
        for (Map.Entry<String, String> entry : configuration.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        
        // Save configuration
        Path configPath = Paths.get(configManager.getBasePath(), "config", configFileName);
        try (FileOutputStream fos = new FileOutputStream(configPath.toFile())) {
            props.store(fos, "Updated by H2H Backend API");
        }
        
        logger.info("Configuration updated for {}: {}", configFileName, configuration.keySet());
    }

    /**
     * Validate configuration for a specific bank and operation type
     */
    public Map<String, Object> validateConfiguration(String bankName, String operationType, String direction) 
            throws IOException {
        
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        try {
            Map<String, String> config = getBankConfiguration(bankName, operationType, direction);
            
            // Validate required fields based on direction
            if ("upload".equalsIgnoreCase(direction)) {
                validateUploadConfig(config, errors, warnings);
            } else if ("download".equalsIgnoreCase(direction)) {
                validateDownloadConfig(config, errors, warnings);
            }
            
            // Common validation
            validateCommonConfig(config, errors, warnings);
            
            result.put("valid", errors.isEmpty());
            result.put("errors", errors);
            result.put("warnings", warnings);
            
        } catch (Exception e) {
            errors.add("Failed to load configuration: " + e.getMessage());
            result.put("valid", false);
            result.put("errors", errors);
            result.put("warnings", warnings);
        }
        
        return result;
    }

    /**
     * Get configuration templates for different operation types
     */
    public Map<String, Map<String, String>> getConfigurationTemplates() {
        Map<String, Map<String, String>> templates = new HashMap<>();
        
        // Upload template
        Map<String, String> uploadTemplate = new HashMap<>();
        uploadTemplate.put("host", "sftp.example.com");
        uploadTemplate.put("port", "22");
        uploadTemplate.put("username", "username");
        uploadTemplate.put("pk_alias", "/path/to/private/key");
        uploadTemplate.put("session_timeout", "60000");
        uploadTemplate.put("channel_timeout", "60000");
        uploadTemplate.put("localDirOut", "/path/to/local/receiver/files");
        uploadTemplate.put("remoteDirIn", "/path/to/remote/sender/directory");
        uploadTemplate.put("archiveDir", "/path/to/archive/directory");
        uploadTemplate.put("filename", "*");
        uploadTemplate.put("extension", "xml");
        uploadTemplate.put("stop_on_error", "false");
        templates.put("upload", uploadTemplate);
        
        // Download template
        Map<String, String> downloadTemplate = new HashMap<>();
        downloadTemplate.put("host", "sftp.example.com");
        downloadTemplate.put("port", "22");
        downloadTemplate.put("username", "username");
        downloadTemplate.put("pk_alias", "/path/to/private/key");
        downloadTemplate.put("session_timeout", "60000");
        downloadTemplate.put("channel_timeout", "60000");
        downloadTemplate.put("localDirIn", "/path/to/local/sender/files");
        downloadTemplate.put("remoteDirOut", "/path/to/remote/receiver/directory");
        downloadTemplate.put("archiveDir", "/path/to/archive/directory");
        downloadTemplate.put("filename", "*");
        downloadTemplate.put("extension", "xml");
        downloadTemplate.put("delete_remote_files", "false");
        downloadTemplate.put("stop_on_error", "false");
        templates.put("download", downloadTemplate);
        
        return templates;
    }

    private String buildConfigFileName(String bankName, String operationType, String direction) {
        return bankName.toLowerCase() + 
               capitalizeFirst(operationType) + 
               capitalizeFirst(direction) + 
               ".config.properties";
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private void validateUploadConfig(Map<String, String> config, List<String> errors, List<String> warnings) {
        if (isEmpty(config.get("localDirOut"))) {
            errors.add("localDirOut is required for upload operations");
        } else {
            Path localDir = Paths.get(config.get("localDirOut"));
            if (!Files.exists(localDir)) {
                warnings.add("Local output directory does not exist: " + config.get("localDirOut"));
            }
        }
        
        if (isEmpty(config.get("remoteDirIn"))) {
            errors.add("remoteDirIn is required for upload operations");
        }
        
        if (isEmpty(config.get("archiveDir"))) {
            errors.add("archiveDir is required for upload operations");
        } else {
            Path archiveDir = Paths.get(config.get("archiveDir"));
            if (!Files.exists(archiveDir)) {
                warnings.add("Archive directory does not exist: " + config.get("archiveDir"));
            }
        }
    }

    private void validateDownloadConfig(Map<String, String> config, List<String> errors, List<String> warnings) {
        if (isEmpty(config.get("localDirIn"))) {
            errors.add("localDirIn is required for download operations");
        } else {
            Path localDir = Paths.get(config.get("localDirIn"));
            if (!Files.exists(localDir)) {
                warnings.add("Local input directory does not exist: " + config.get("localDirIn"));
            }
        }
        
        if (isEmpty(config.get("remoteDirOut"))) {
            errors.add("remoteDirOut is required for download operations");
        }
        
        if (isEmpty(config.get("archiveDir"))) {
            errors.add("archiveDir is required for download operations");
        } else {
            Path archiveDir = Paths.get(config.get("archiveDir"));
            if (!Files.exists(archiveDir)) {
                warnings.add("Archive directory does not exist: " + config.get("archiveDir"));
            }
        }
    }

    private void validateCommonConfig(Map<String, String> config, List<String> errors, List<String> warnings) {
        if (isEmpty(config.get("host"))) {
            errors.add("host is required");
        }
        
        if (isEmpty(config.get("username"))) {
            errors.add("username is required");
        }
        
        if (isEmpty(config.get("pk_alias"))) {
            errors.add("pk_alias (private key path) is required");
        } else {
            Path keyPath = Paths.get(config.get("pk_alias"));
            if (!Files.exists(keyPath)) {
                errors.add("Private key file does not exist: " + config.get("pk_alias"));
            }
        }
        
        // Validate numeric fields
        validateNumericField(config, "port", errors);
        validateNumericField(config, "session_timeout", errors);
        validateNumericField(config, "channel_timeout", errors);
    }

    private void validateNumericField(Map<String, String> config, String fieldName, List<String> errors) {
        String value = config.get(fieldName);
        if (!isEmpty(value)) {
            try {
                Integer.parseInt(value);
            } catch (NumberFormatException e) {
                errors.add(fieldName + " must be a valid number: " + value);
            }
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}