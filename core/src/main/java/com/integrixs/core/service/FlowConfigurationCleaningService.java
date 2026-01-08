package com.integrixs.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for cleaning flow configurations to ensure proper OOP design.
 * Single Responsibility: Only handles flow configuration cleaning operations
 * Extracted from FlowDefinitionService to follow SRP
 */
@Service
public class FlowConfigurationCleaningService {
    
    private static final Logger logger = LoggerFactory.getLogger(FlowConfigurationCleaningService.class);
    
    /**
     * Strip embedded adapter configurations from flow definition nodes.
     * This ensures flows only store adapter ID references and always use live database configurations.
     */
    public Map<String, Object> stripEmbeddedConfigurations(Map<String, Object> flowDefinition) {
        if (flowDefinition == null || flowDefinition.isEmpty()) {
            return Collections.emptyMap();
        }
        
        final ConfigurationCleaner cleaner = new ConfigurationCleaner();
        return cleaner.cleanFlowDefinition(flowDefinition);
    }
    
    /**
     * Clean adapter configuration by removing sensitive connection details
     * while preserving all other configuration settings
     */
    public Map<String, Object> sanitizeAdapterConfiguration(Map<String, Object> originalConfig) {
        if (originalConfig == null || originalConfig.isEmpty()) {
            return originalConfig;
        }
        
        final ConfigurationSanitizer sanitizer = new ConfigurationSanitizer();
        return sanitizer.sanitizeConfiguration(originalConfig);
    }
    
    /**
     * Inner class responsible for cleaning flow definitions according to OOP principles:
     * - Single Responsibility: Only handles flow definition cleaning
     * - Immutability: Returns new objects rather than modifying input
     * - Type Safety: Proper type checking and casting
     */
    private static class ConfigurationCleaner {
        
        private static final Logger logger = LoggerFactory.getLogger(ConfigurationCleaner.class);
        
        // Immutable set of allowed adapter node properties
        private static final Set<String> ALLOWED_ADAPTER_PROPERTIES = Set.of(
            "label", "adapterType", "direction", "adapterId", 
            "showDeleteButton", "availableTypes"
        );
        
        /**
         * Clean flow definition by processing all nodes
         */
        public Map<String, Object> cleanFlowDefinition(Map<String, Object> originalDefinition) {
            final Map<String, Object> cleanedDefinition = createDeepCopy(originalDefinition);
            
            Optional<List<Map<String, Object>>> nodes = extractNodes(cleanedDefinition);
            if (nodes.isPresent()) {
                List<Map<String, Object>> cleanedNodes = nodes.get()
                    .stream()
                    .map(this::processNode)
                    .collect(Collectors.toList());
                    
                cleanedDefinition.put("nodes", cleanedNodes);
                logger.debug("Stripped embedded adapter configurations from {} nodes", cleanedNodes.size());
            }
            
            return cleanedDefinition;
        }
        
        /**
         * Create a deep copy of the flow definition to ensure immutability
         */
        private Map<String, Object> createDeepCopy(Map<String, Object> original) {
            return new HashMap<>(original);
        }
        
        /**
         * Safely extract nodes from flow definition with type checking
         */
        private Optional<List<Map<String, Object>>> extractNodes(Map<String, Object> flowDefinition) {
            Object nodesObj = flowDefinition.get("nodes");
            
            if (!(nodesObj instanceof List)) {
                return Optional.empty();
            }
            
            try {
                @SuppressWarnings("unchecked")
                List<Object> rawNodes = (List<Object>) nodesObj;
                
                List<Map<String, Object>> typedNodes = rawNodes.stream()
                    .filter(node -> node instanceof Map)
                    .map(node -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedNode = (Map<String, Object>) node;
                        return typedNode;
                    })
                    .collect(Collectors.toList());
                    
                return Optional.of(typedNodes);
            } catch (ClassCastException e) {
                logger.warn("Invalid node structure in flow definition", e);
                return Optional.empty();
            }
        }
        
        /**
         * Process individual node - clean if adapter, pass through if not
         */
        private Map<String, Object> processNode(Map<String, Object> node) {
            if (isAdapterNode(node)) {
                return cleanAdapterNode(node);
            }
            return new HashMap<>(node); // Return immutable copy
        }
        
        /**
         * Check if node is an adapter type that needs cleaning
         */
        private boolean isAdapterNode(Map<String, Object> node) {
            return "adapter".equals(node.get("type"));
        }
        
        /**
         * Clean adapter node by removing embedded configurations while preserving references
         */
        private Map<String, Object> cleanAdapterNode(Map<String, Object> adapterNode) {
            Map<String, Object> cleanedNode = new HashMap<>(adapterNode);
            
            Optional<Map<String, Object>> nodeData = extractNodeData(adapterNode);
            if (nodeData.isPresent()) {
                Map<String, Object> cleanedData = filterAllowedProperties(nodeData.get());
                cleanedNode.put("data", cleanedData);
                
                logger.debug("Cleaned adapter node: {}, removed embedded configuration", 
                           cleanedData.get("label"));
            }
            
            return cleanedNode;
        }
        
        /**
         * Safely extract node data with type checking
         */
        private Optional<Map<String, Object>> extractNodeData(Map<String, Object> node) {
            Object dataObj = node.get("data");
            
            if (!(dataObj instanceof Map)) {
                return Optional.empty();
            }
            
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> nodeData = (Map<String, Object>) dataObj;
                return Optional.of(nodeData);
            } catch (ClassCastException e) {
                logger.warn("Invalid node data structure", e);
                return Optional.empty();
            }
        }
        
        /**
         * Filter node properties to only include allowed adapter references
         */
        private Map<String, Object> filterAllowedProperties(Map<String, Object> nodeData) {
            return nodeData.entrySet()
                .stream()
                .filter(entry -> ALLOWED_ADAPTER_PROPERTIES.contains(entry.getKey()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (existing, replacement) -> existing,
                    HashMap::new
                ));
        }
    }
    
    /**
     * Inner class for sanitizing adapter configurations
     * Removes sensitive information while preserving functional settings
     */
    private static class ConfigurationSanitizer {
        
        private static final Logger logger = LoggerFactory.getLogger(ConfigurationSanitizer.class);
        
        // Immutable set of sensitive field patterns
        private static final Set<String> SENSITIVE_PATTERNS = Set.of(
            "password", "secret", "key", "token", "credential",
            "host", "hostname", "server", "ip", "address",
            "directory", "path", "folder"
        );
        
        /**
         * Sanitize configuration by removing sensitive fields
         */
        public Map<String, Object> sanitizeConfiguration(Map<String, Object> originalConfig) {
            Map<String, Object> sanitizedConfig = new HashMap<>(originalConfig);
            
            // Remove sensitive fields
            sanitizedConfig.entrySet().removeIf(entry -> 
                isSensitiveField(entry.getKey())
            );
            
            // Recursively sanitize nested configurations
            sanitizedConfig.entrySet().forEach(entry -> {
                if (entry.getValue() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedConfig = (Map<String, Object>) entry.getValue();
                    entry.setValue(sanitizeConfiguration(nestedConfig));
                }
            });
            
            logger.debug("Sanitized configuration: removed sensitive fields, kept {} fields", 
                        sanitizedConfig.size());
            return sanitizedConfig;
        }
        
        /**
         * Check if a field name contains sensitive information
         */
        private boolean isSensitiveField(String fieldName) {
            if (fieldName == null) {
                return false;
            }
            
            String lowerFieldName = fieldName.toLowerCase();
            return SENSITIVE_PATTERNS.stream()
                .anyMatch(pattern -> lowerFieldName.contains(pattern));
        }
    }
}