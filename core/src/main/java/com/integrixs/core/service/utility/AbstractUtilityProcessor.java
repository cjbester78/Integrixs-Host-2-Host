package com.integrixs.core.service.utility;

import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for utility processors
 * Provides common functionality and patterns for utility execution
 */
public abstract class AbstractUtilityProcessor {
    
    protected static final Logger logger = LoggerFactory.getLogger(AbstractUtilityProcessor.class);
    protected static final Marker UTILITY_MARKER = MarkerFactory.getMarker("UTILITY_EXECUTION");
    
    /**
     * Execute utility operation with context
     */
    public abstract Map<String, Object> executeUtility(
            FlowExecutionStep step, 
            Map<String, Object> context, 
            Map<String, Object> configuration);
    
    /**
     * Get the utility type this processor handles
     */
    public abstract String getUtilityType();
    
    /**
     * Create standardized result map
     */
    protected Map<String, Object> createResult(boolean success, String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        result.put("timestamp", LocalDateTime.now());
        result.put("utilityType", getUtilityType());
        
        if (data != null) {
            result.put("data", data);
        }
        
        return result;
    }
    
    /**
     * Create success result
     */
    protected Map<String, Object> createSuccessResult(String message, Object data) {
        return createResult(true, message, data);
    }
    
    /**
     * Create error result
     */
    protected Map<String, Object> createErrorResult(String message, Exception e) {
        Map<String, Object> result = createResult(false, message, null);
        if (e != null) {
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
        }
        return result;
    }
    
    /**
     * Validate required configuration parameters
     */
    protected void validateConfiguration(Map<String, Object> config, String... requiredKeys) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        
        for (String key : requiredKeys) {
            if (!config.containsKey(key) || config.get(key) == null) {
                throw new IllegalArgumentException("Required configuration parameter missing: " + key);
            }
        }
    }
    
    /**
     * Get configuration value with default
     */
    protected <T> T getConfigValue(Map<String, Object> config, String key, T defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            @SuppressWarnings("unchecked")
            T result = (T) value;
            return result;
        } catch (ClassCastException e) {
            logger.warn("Configuration value '{}' has incorrect type, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Ensure directory exists
     */
    protected void ensureDirectoryExists(String directoryPath) throws IOException {
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Directory path cannot be null or empty");
        }
        
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            logger.debug("Created directory: {}", directoryPath);
        }
    }
    
    /**
     * Validate file path for security (prevent directory traversal)
     */
    protected void validateFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty");
        }
        
        // Basic security check for directory traversal
        if (filePath.contains("..") || filePath.contains("~")) {
            throw new SecurityException("Invalid file path detected: " + filePath);
        }
        
        // Normalize the path
        Path path = Paths.get(filePath).normalize();
        if (!path.equals(Paths.get(filePath))) {
            throw new SecurityException("Potentially unsafe file path: " + filePath);
        }
    }
    
    /**
     * Get file extension from path
     */
    protected String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
            return "";
        }
        
        return filePath.substring(lastDotIndex + 1).toLowerCase();
    }
    
    /**
     * Log utility execution start
     */
    protected void logExecutionStart(FlowExecutionStep step, String operation) {
        logger.info(UTILITY_MARKER, "Starting {} operation for step {} (execution: {})", 
            operation, step.getId(), step.getExecutionId());
    }
    
    /**
     * Log utility execution completion
     */
    protected void logExecutionComplete(FlowExecutionStep step, String operation, long durationMs) {
        logger.info(UTILITY_MARKER, "Completed {} operation for step {} in {}ms", 
            operation, step.getId(), durationMs);
    }
    
    /**
     * Log utility execution error
     */
    protected void logExecutionError(FlowExecutionStep step, String operation, Exception e) {
        logger.error(UTILITY_MARKER, "Failed {} operation for step {}: {}", 
            operation, step.getId(), e.getMessage(), e);
    }
    
    /**
     * Update execution context with result data
     */
    protected void updateExecutionContext(Map<String, Object> context, String key, Object value) {
        if (context != null && key != null) {
            context.put(key, value);
            logger.debug("Updated execution context: {} = {}", key, value);
        }
    }
    
    /**
     * Generate unique filename with timestamp
     */
    protected String generateUniqueFilename(String baseName, String extension) {
        String timestamp = LocalDateTime.now().toString().replace(":", "-");
        return String.format("%s_%s.%s", baseName, timestamp, extension);
    }
}