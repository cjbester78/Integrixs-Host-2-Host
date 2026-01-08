package com.integrixs.adapters;

// Removed unused import
import com.integrixs.core.adapter.AbstractAdapterExecutor;
import com.integrixs.core.service.AdapterExecutionService;
import com.integrixs.adapters.AdapterExecutorFactory;
import org.springframework.stereotype.Service;
import com.integrixs.core.repository.AdapterRepository;
import com.integrixs.core.repository.SystemConfigurationRepository;
import com.integrixs.core.logging.EnhancedLogger;
import com.integrixs.core.logging.CorrelationContext;
import com.integrixs.shared.model.Adapter;
import com.integrixs.shared.model.SshKey;
import com.integrixs.shared.model.FlowExecutionStep;
import com.integrixs.core.repository.SshKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
 * specialized adapter executor classes through the {@link AdapterExecutorFactory}. The service
 * follows the Single Responsibility Principle and uses the Factory Method pattern for 
 * creating appropriate adapter executors.</p>
 * @author H2H Development Team
 * @version 2.0 (Refactored to OOP)
 * @see AdapterExecutorFactory
 * @see AdapterExecutor
 * @since 1.0
 */
@Service
public class AdapterExecutionServiceImpl implements AdapterExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdapterExecutionServiceImpl.class);
    private final EnhancedLogger enhancedLogger = EnhancedLogger.getLogger(AdapterExecutionServiceImpl.class);
    private static final Marker ADAPTER_EXECUTION_MARKER = MarkerFactory.getMarker("ADAPTER_EXECUTION");
    
    private final AdapterRepository adapterRepository;
    private final SystemConfigurationRepository configRepository;
    private final AdapterExecutorFactory adapterExecutorFactory;
    private final SshKeyRepository sshKeyRepository;
    private final Map<UUID, Object> adapterCache = new ConcurrentHashMap<>();
    
    @Autowired
    public AdapterExecutionServiceImpl(AdapterRepository adapterRepository,
                                     SystemConfigurationRepository configRepository,
                                     AdapterExecutorFactory adapterExecutorFactory,
                                     SshKeyRepository sshKeyRepository) {
        this.adapterRepository = adapterRepository;
        this.configRepository = configRepository;
        this.adapterExecutorFactory = adapterExecutorFactory;
        this.sshKeyRepository = sshKeyRepository;
    }
    

    @Override
    public Map<String, Object> executeAdapter(Adapter adapter, Map<String, Object> executionContext, 
                                             FlowExecutionStep step) {
        logger.info(ADAPTER_EXECUTION_MARKER, "Executing adapter: {} for step: {}", adapter.getId(), step != null ? step.getId() : "polling");
        
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter cannot be null");
        }
        
        if (!adapter.isActive()) {
            throw new RuntimeException("Adapter is inactive: " + adapter.getId());
        }
        
        return executeAdapterInternal(adapter, executionContext, step);
    }

    /**
     * Execute adapter operation as part of flow execution (legacy method - reads live config)
     */
    public Map<String, Object> executeAdapter(UUID adapterId, Map<String, Object> executionContext, 
                                             FlowExecutionStep step) {
        logger.info(ADAPTER_EXECUTION_MARKER, "Executing adapter: {} for step: {}", adapterId, step != null ? step.getId() : "polling");
        
        Optional<Adapter> adapterOpt = adapterRepository.findById(adapterId);
        if (adapterOpt.isEmpty()) {
            throw new RuntimeException("Adapter not found: " + adapterId);
        }
        
        Adapter adapter = adapterOpt.get();
        
        // Delegate to main implementation
        return executeAdapter(adapter, executionContext, step);
    }

    private Map<String, Object> executeAdapterInternal(Adapter adapter, Map<String, Object> executionContext, 
                                                      FlowExecutionStep step) {
        
        // Set adapter context for logging correlation
        CorrelationContext.setAdapterId(adapter.getId().toString());
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
            result.put("adapterId", adapter.getId());
            result.put("adapterName", adapter.getName());
            result.put("adapterType", adapter.getAdapterType());
            result.put("direction", adapter.getDirection());
            result.put("executionStartTime", LocalDateTime.now());
            
            // Use factory pattern to get appropriate adapter executor
            try {
                Optional<AbstractAdapterExecutor> executorOpt = adapterExecutorFactory.createAdapter(adapter.getAdapterType(), adapter.getDirection());
                if (executorOpt.isEmpty()) {
                    throw new UnsupportedOperationException("No adapter executor found for type: " + adapter.getAdapterType() + ", direction: " + adapter.getDirection());
                }
                AbstractAdapterExecutor executor = executorOpt.get();
                logger.info(ADAPTER_EXECUTION_MARKER, "Using {} adapter executor for {}", 
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
            
            logger.info(ADAPTER_EXECUTION_MARKER, "Adapter execution completed successfully: {}", adapter.getId());
            return result;
            
        } catch (Exception e) {
            logger.error("Adapter execution failed for {}: {}", adapter.getId(), e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("adapterId", adapter.getId());
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
     * Find SSH key for SFTP adapter using the SSH Key Service
     */
    private Optional<SshKey> findSshKey(String keyName) {
        if (keyName == null || keyName.trim().isEmpty()) {
            logger.debug("No SSH key name provided, using password authentication");
            return Optional.empty();
        }
        
        try {
            logger.debug("Looking up SSH key by name: {}", keyName);
            Optional<SshKey> sshKey = sshKeyRepository.findByName(keyName);
            
            if (sshKey.isPresent()) {
                logger.info("Found SSH key: {} for SFTP authentication", keyName);
                return sshKey;
            } else {
                logger.warn("SSH key not found: {}, falling back to password authentication", keyName);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Error looking up SSH key: {}, falling back to password authentication: {}", keyName, e.getMessage());
            return Optional.empty();
        }
    }
    
    
    /**
     * Test adapter connection
     */
    @Override
    public Map<String, Object> testAdapterConnection(Adapter adapter) {
        logger.info("Testing adapter connection: {}", adapter.getId());
        
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter cannot be null");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("adapterId", adapter.getId());
        result.put("adapterType", adapter.getAdapterType());
        result.put("testTime", LocalDateTime.now());
        
        try {
            // Use the OOP adapter classes for testing instead of duplicate code
            Optional<AbstractAdapterExecutor> executorOpt = adapterExecutorFactory.createAdapter(adapter.getAdapterType(), adapter.getDirection());
            if (executorOpt.isEmpty()) {
                throw new UnsupportedOperationException("No adapter executor found for type: " + adapter.getAdapterType() + ", direction: " + adapter.getDirection());
            }
            AbstractAdapterExecutor executor = executorOpt.get();
            
            // Validate configuration using the proper adapter class
            executor.validateConfiguration(adapter);
            
            result.put("adapterType", adapter.getAdapterType());
            result.put("direction", adapter.isSender() ? "SENDER" : "RECEIVER");
            result.put("executorClass", executor.getClass().getSimpleName());
            result.put("configurationValid", true);
            result.put("success", true);
            
            logger.info("Adapter configuration test successful: {} using {}", 
                       adapter.getId(), executor.getClass().getSimpleName());
            
        } catch (Exception e) {
            logger.error("Adapter configuration test failed for {}: {}", adapter.getId(), e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("configurationValid", false);
        }
        
        return result;
    }
    
    @Override
    public boolean isValidAdapter(Adapter adapter) {
        if (adapter == null) {
            return false;
        }
        
        try {
            // Check if we can create an executor for this adapter type and direction
            Optional<AbstractAdapterExecutor> executorOpt = adapterExecutorFactory.createAdapter(
                adapter.getAdapterType(), adapter.getDirection());
                
            if (executorOpt.isEmpty()) {
                logger.debug("No executor found for adapter type: {} direction: {}", 
                           adapter.getAdapterType(), adapter.getDirection());
                return false;
            }
            
            // Try to validate configuration
            AbstractAdapterExecutor executor = executorOpt.get();
            executor.validateConfiguration(adapter);
            
            logger.debug("Adapter validation successful: {} ({})", adapter.getName(), adapter.getId());
            return true;
            
        } catch (Exception e) {
            logger.debug("Adapter validation failed for {}: {}", adapter.getName(), e.getMessage());
            return false;
        }
    }
    
    // Legacy test methods removed - now using OOP adapter classes for testing
    // Configuration validation is handled by each adapter executor's validateConfiguration() method
    
}