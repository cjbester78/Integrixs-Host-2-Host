package com.integrixs.core.service;

import com.integrixs.core.repository.FlowUtilityRepository;
import com.integrixs.core.service.utility.*;
import com.integrixs.shared.model.FlowUtility;
import com.integrixs.shared.model.FlowExecutionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for executing utility operations within flows - Factory Pattern
 * Delegates to focused utility processors following Single Responsibility Principle
 * 
 * This service has been refactored from a monolithic 1,059-line class into focused processors:
 * - PgpUtilityProcessor: PGP encryption and decryption operations
 * - FileUtilityProcessor: File split, merge, copy, move, validate operations
 * - CompressionUtilityProcessor: ZIP compress, extract, list, validate operations
 * - DataUtilityProcessor: CSV and XML transform, parse, validate operations
 * 
 * This factory maintains backward compatibility while providing clean separation of concerns.
 */
@Service
public class UtilityExecutionService {
    
    private static final Logger logger = LoggerFactory.getLogger(UtilityExecutionService.class);
    private static final Marker ADAPTER_EXECUTION_MARKER = MarkerFactory.getMarker("ADAPTER_EXECUTION");
    
    private final FlowUtilityRepository utilityRepository;
    private final Map<UUID, FlowUtility> utilityCache = new HashMap<>();
    
    // Focused utility processors
    private final PgpUtilityProcessor pgpProcessor;
    private final FileUtilityProcessor fileProcessor;
    private final CompressionUtilityProcessor compressionProcessor;
    private final DataUtilityProcessor dataProcessor;
    
    @Autowired
    public UtilityExecutionService(
            FlowUtilityRepository utilityRepository,
            PgpUtilityProcessor pgpProcessor,
            FileUtilityProcessor fileProcessor,
            CompressionUtilityProcessor compressionProcessor,
            DataUtilityProcessor dataProcessor) {
        this.utilityRepository = utilityRepository;
        this.pgpProcessor = pgpProcessor;
        this.fileProcessor = fileProcessor;
        this.compressionProcessor = compressionProcessor;
        this.dataProcessor = dataProcessor;
    }
    
    /**
     * Execute utility operation as part of flow execution - Factory Method
     * Delegates to appropriate focused utility processor
     */
    public Map<String, Object> executeUtility(String utilityType, Map<String, Object> configuration, 
                                             Map<String, Object> executionContext, FlowExecutionStep step) {
        logger.info(ADAPTER_EXECUTION_MARKER, "Executing utility: {} for step: {}", utilityType, step.getId());
        
        try {
            // Route to appropriate processor based on utility type
            AbstractUtilityProcessor processor = getUtilityProcessor(utilityType);
            Map<String, Object> processorConfig = createProcessorConfiguration(utilityType, configuration);
            
            // Execute through focused processor
            Map<String, Object> result = processor.executeUtility(step, executionContext, processorConfig);
            
            // Add compatibility wrapper for legacy format
            result.put("utilityType", utilityType);
            result.put("executionStartTime", result.getOrDefault("timestamp", LocalDateTime.now()));
            result.put("executionEndTime", LocalDateTime.now());
            result.put("executionStatus", (Boolean) result.getOrDefault("success", false) ? "SUCCESS" : "FAILED");
            
            logger.info(ADAPTER_EXECUTION_MARKER, "Utility execution completed successfully: {}", utilityType);
            return result;
            
        } catch (Exception e) {
            logger.error("Utility execution failed for {}: {}", utilityType, e.getMessage(), e);
            
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("utilityType", utilityType);
            errorResult.put("executionStatus", "FAILED");
            errorResult.put("errorMessage", e.getMessage());
            errorResult.put("executionStartTime", LocalDateTime.now());
            errorResult.put("executionEndTime", LocalDateTime.now());
            errorResult.put("success", false);
            
            throw new RuntimeException("Utility execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get appropriate utility processor for the given utility type
     */
    private AbstractUtilityProcessor getUtilityProcessor(String utilityType) {
        String type = utilityType.toUpperCase();
        
        // Route PGP operations
        if (type.startsWith("PGP_") || type.equals("PGP")) {
            return pgpProcessor;
        }
        
        // Route file operations
        if (type.startsWith("FILE_") || Arrays.asList("SPLIT", "MERGE", "COPY", "MOVE", "VALIDATE", "HASH").contains(type)) {
            return fileProcessor;
        }
        
        // Route compression operations
        if (type.startsWith("ZIP_") || Arrays.asList("COMPRESS", "EXTRACT", "COMPRESSION").contains(type)) {
            return compressionProcessor;
        }
        
        // Route data processing operations
        if (type.startsWith("DATA_") || type.startsWith("CSV_") || type.startsWith("XML_") || 
            Arrays.asList("TRANSFORM", "PARSE", "CSV", "XML", "XPATH").contains(type)) {
            return dataProcessor;
        }
        
        throw new IllegalArgumentException("Unsupported utility type: " + utilityType + 
            ". Supported processors: PGP, FILE, COMPRESSION, DATA");
    }
    
    /**
     * Create processor-specific configuration from legacy utility configuration
     */
    private Map<String, Object> createProcessorConfiguration(String utilityType, Map<String, Object> configuration) {
        Map<String, Object> processorConfig = new HashMap<>(configuration);
        String type = utilityType.toUpperCase();
        
        // Map legacy utility types to processor operations
        if (type.equals("PGP_ENCRYPT") || type.equals("PGP_DECRYPT")) {
            processorConfig.put("operation", type.substring(4).toLowerCase()); // "encrypt" or "decrypt"
        } else if (type.equals("ZIP_COMPRESS")) {
            processorConfig.put("operation", "compress");
        } else if (type.equals("ZIP_EXTRACT")) {
            processorConfig.put("operation", "extract");
        } else if (type.equals("FILE_SPLIT")) {
            processorConfig.put("operation", "split");
        } else if (type.equals("FILE_MERGE")) {
            processorConfig.put("operation", "merge");
        } else if (type.equals("FILE_VALIDATE")) {
            processorConfig.put("operation", "validate");
        } else if (type.equals("DATA_TRANSFORM")) {
            // Default to CSV transform, or use dataType if specified
            processorConfig.put("operation", "transform");
            processorConfig.putIfAbsent("dataType", "csv");
        } else {
            // For direct processor operations, extract operation from type
            String[] parts = type.split("_", 2);
            if (parts.length == 2) {
                processorConfig.put("operation", parts[1].toLowerCase());
            } else {
                processorConfig.put("operation", type.toLowerCase());
            }
        }
        
        return processorConfig;
    }
    
    // ===== Utility Cache Management =====
    
    /**
     * Get utility by ID from repository with caching
     */
    public FlowUtility getUtilityById(UUID utilityId) {
        if (utilityCache.containsKey(utilityId)) {
            return utilityCache.get(utilityId);
        }
        
        FlowUtility utility = utilityRepository.findById(utilityId).orElse(null);
        if (utility != null) {
            utilityCache.put(utilityId, utility);
        }
        
        return utility;
    }
    
    /**
     * Clear utility cache
     */
    public void clearUtilityCache() {
        utilityCache.clear();
        logger.debug("Utility cache cleared");
    }
    
    /**
     * Get cached utility count
     */
    public int getCachedUtilityCount() {
        return utilityCache.size();
    }
}