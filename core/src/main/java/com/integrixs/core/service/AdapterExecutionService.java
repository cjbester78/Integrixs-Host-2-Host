package com.integrixs.core.service;

import com.integrixs.core.adapter.AdapterExecutor;
import com.integrixs.core.factory.AdapterFactory;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.SystemConfigurationRepository;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.core.logging.CorrelationContext;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.SshKey;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Removed mail imports - not needed currently

/**
 * Service for executing adapter operations using Object-Oriented Programming patterns.
 * 
 * <p>This service acts as the main coordinator for adapter execution, delegating to 
 * specialized adapter executor classes through the {@link AdapterFactory}. The service
 * follows the Single Responsibility Principle and uses the Factory Method pattern for 
 * creating appropriate adapter executors.</p>
 * @author H2H Development Team
 * @version 2.0 (Refactored to OOP)
 * @see AdapterFactory
 * @see AdapterExecutor
 * @since 1.0
 */
@Service
public class AdapterExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterExecutionService.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(AdapterExecutionService.class);
    
    private final AdapterRepository adapterRepository;
    private final SystemConfigurationRepository configRepository;
    // Removed SshKeyService - will use JSch directly
    private final Map<UUID, Object> adapterCache = new ConcurrentHashMap<>();
    
    @Autowired
    public AdapterExecutionService(AdapterRepository adapterRepository,
                                 SystemConfigurationRepository configRepository) {
        this.adapterRepository = adapterRepository;
        this.configRepository = configRepository;
    }
    

    /**
     * Execute adapter operation as part of flow execution (legacy method - reads live config)
     */
    public Map<String, Object> executeAdapter(UUID adapterId, Map<String, Object> executionContext, 
                                             FlowExecutionStep step) {
        logger.info("Executing adapter: {} for step: {}", adapterId, step != null ? step.getId() : "polling");
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (adapterOpt.isEmpty()) {
            throw new RuntimeException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        if (!adapter.isActive()) {
            throw new RuntimeException("Adapter is inactive: " + adapterId);
        }
        
        // Set adapter context for logging correlation
        CorrelationContext.setAdapterId(adapterId.toString());
        CorrelationContext.setAdapterName(adapter.getName());
        if (executionContext.containsKey("executionId")) {
            CorrelationContext.setExecutionId(executionContext.get("executionId").toString());
        }
        if (executionContext.containsKey("flowId")) {
            CorrelationContext.setFlowId(executionContext.get("flowId").toString());
        }
        if (executionContext.containsKey("flowName")) {
            CorrelationContext.setFlowName(executionContext.get("flowName").toString());
        }
        
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("adapterId", adapterId);
            result.put("adapterName", adapter.getName());
            result.put("adapterType", adapter.getAdapterType());
            result.put("direction", adapter.getDirection());
            result.put("executionStartTime", LocalDateTime.now());
            
            // Use factory pattern to get appropriate adapter executor
            try {
                AdapterExecutor executor = AdapterFactory.createExecutor(adapter);
                logger.info("Using {} adapter executor for {}", 
                           executor.getClass().getSimpleName(), adapter.getName());
                
                // Execute using the specific adapter executor
                Map<String, Object> executionResult = executor.execute(adapter, executionContext, step);
                result.putAll(executionResult);
                
            } catch (UnsupportedOperationException e) {
                logger.error("Unsupported adapter type/direction: {} - {}", 
                           adapter.getAdapterType(), e.getMessage());
                
                // All adapters now use the OOP factory pattern (SFTP, FILE, EMAIL)
                logger.error("Adapter type {} with direction {} is not supported", 
                           adapter.getAdapterType(), adapter.isSender() ? "SENDER" : "RECEIVER");
                throw new RuntimeException("Unsupported adapter type/direction combination: " + 
                                         adapter.getAdapterType() + "/" + (adapter.isSender() ? "SENDER" : "RECEIVER"));
            }
            
            result.put("executionEndTime", LocalDateTime.now());
            result.put("executionStatus", "SUCCESS");
            
            logger.info("Adapter execution completed successfully: {}", adapterId);
            return result;
            
        } catch (Exception e) {
            logger.error("Adapter execution failed for {}: {}", adapterId, e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("adapterId", adapterId);
            errorResult.put("adapterName", adapter.getName());
            errorResult.put("executionStatus", "FAILED");
            errorResult.put("errorMessage", e.getMessage());
            errorResult.put("executionEndTime", LocalDateTime.now());
            errorResult.put("hasData", false);
            
            throw new RuntimeException("Adapter execution failed: " + e.getMessage(), e);
        }
    }
    

    
    /**
     * Check if file is ready for processing (not being written to)
     */
    private boolean isFileReady(Path filePath) {
        try {
            // Check if file can be moved (indicates it's not locked)
            Path tempPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.move(filePath, tempPath);
            Files.move(tempPath, filePath);
            return true;
        } catch (IOException e) {
            logger.debug("File not ready for processing: {}", filePath);
            return false;
        }
    }
    
    /**
     * Check if filename matches pattern (supports wildcards)
     */
    private boolean matchesPattern(String fileName, String pattern) {
        if (pattern == null || pattern.equals("*")) {
            return true;
        }
        
        // Simple wildcard matching
        if (pattern.contains("*")) {
            String regex = pattern.replace("*", ".*").replace("?", ".");
            return fileName.matches(regex);
        }
        
        return fileName.equals(pattern);
    }
    

    /**
     * Execute real File adapter receiver operation
     * Writes file content from context to target directory
     * After successful processing, archives source files
     */
    
    /**
     * Archive source files after successful receiver processing
     * This method is called ONLY after files have been successfully written to receiver
     */
    
    // Legacy SFTP execution methods removed - now using OOP adapter classes via factory pattern
    // All SFTP operations handled by SftpSenderAdapter and SftpReceiverAdapter classes
    
    /**
     * Find SSH key for SFTP adapter
     */
    private Optional<SshKey> findSshKey(String keyName) {
        // Implementation would look up SSH key from database
        // For now, return empty to use password authentication
        return Optional.empty();
    }
    
    
    /**
     * Test adapter connection
     */
    public Map<String, Object> testAdapterConnection(UUID adapterId) {
        logger.info("Testing adapter connection: {}", adapterId);
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (adapterOpt.isEmpty()) {
            throw new IllegalArgumentException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("adapterId", adapterId);
        result.put("adapterType", adapter.getAdapterType());
        result.put("testTime", LocalDateTime.now());
        
        try {
            // Use the OOP adapter classes for testing instead of duplicate code
            AdapterExecutor executor = AdapterFactory.createExecutor(adapter);
            
            // Validate configuration using the proper adapter class
            executor.validateConfiguration(adapter);
            
            result.put("adapterType", adapter.getAdapterType());
            result.put("direction", adapter.isSender() ? "SENDER" : "RECEIVER");
            result.put("executorClass", executor.getClass().getSimpleName());
            result.put("configurationValid", true);
            result.put("success", true);
            
            logger.info("Adapter configuration test successful: {} using {}", 
                       adapterId, executor.getClass().getSimpleName());
            
        } catch (Exception e) {
            logger.error("Adapter configuration test failed for {}: {}", adapterId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("configurationValid", false);
        }
        
        return result;
    }
    
    // Legacy test methods removed - now using OOP adapter classes for testing
    // Configuration validation is handled by each adapter executor's validateConfiguration() method
    
}