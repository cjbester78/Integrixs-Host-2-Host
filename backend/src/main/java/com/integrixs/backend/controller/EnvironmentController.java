package com.integrixs.backend.controller;

import com.integrixs.backend.config.EnvironmentConfig;
import com.integrixs.backend.dto.ApiResponse;
import com.integrixs.shared.enums.EnvironmentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for environment configuration management
 */
@RestController
@RequestMapping("/api/system/environment")
public class EnvironmentController {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentController.class);
    
    @Autowired
    private EnvironmentConfig environmentConfig;
    
    /**
     * Get current environment configuration
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentEnvironment() {
        try {
            Map<String, Object> envInfo = environmentConfig.getEnvironmentInfo();
            logger.debug("Retrieved environment configuration: {}", envInfo.get("type"));
            
            return ResponseEntity.ok(ApiResponse.success("Environment configuration retrieved", envInfo));
        } catch (Exception e) {
            logger.error("Error retrieving environment configuration", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve environment configuration: " + e.getMessage()));
        }
    }
    
    /**
     * Update environment configuration
     */
    @PutMapping
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateEnvironment(
            @RequestBody UpdateEnvironmentRequest request) {
        try {
            logger.info("Updating environment configuration: type={}, enforce={}, message={}", 
                       request.getType(), request.getEnforceRestrictions(), 
                       request.getRestrictionMessage() != null ? "***" : null);
            
            EnvironmentType newType = null;
            if (request.getType() != null) {
                try {
                    newType = EnvironmentType.valueOf(request.getType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid environment type: " + request.getType()));
                }
            }
            
            environmentConfig.updateEnvironment(
                newType, 
                request.getEnforceRestrictions(), 
                request.getRestrictionMessage()
            );
            
            // Reload configuration
            environmentConfig.loadConfiguration();
            
            Map<String, Object> updatedInfo = environmentConfig.getEnvironmentInfo();
            logger.info("Environment configuration updated successfully to: {}", updatedInfo.get("type"));
            
            return ResponseEntity.ok(ApiResponse.success("Environment configuration updated", updatedInfo));
        } catch (Exception e) {
            logger.error("Error updating environment configuration", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to update environment configuration: " + e.getMessage()));
        }
    }
    
    /**
     * Get available environment types
     */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getEnvironmentTypes() {
        try {
            List<Map<String, String>> types = Arrays.stream(EnvironmentType.values())
                    .map(type -> {
                        Map<String, String> typeInfo = new HashMap<>();
                        typeInfo.put("name", type.name());
                        typeInfo.put("displayName", type.getDisplayName());
                        typeInfo.put("description", type.getDescription());
                        typeInfo.put("textColorClass", type.getTextColorClass());
                        typeInfo.put("backgroundColorClass", type.getBackgroundColorClass());
                        return typeInfo;
                    })
                    .collect(Collectors.toList());
                    
            logger.debug("Returning {} environment types", types.size());
            return ResponseEntity.ok(ApiResponse.success("Environment types retrieved", types));
        } catch (Exception e) {
            logger.error("Error retrieving environment types", e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to retrieve environment types: " + e.getMessage()));
        }
    }
    
    /**
     * Check specific permission
     */
    @GetMapping("/permissions/{action}")
    public ResponseEntity<ApiResponse<Boolean>> checkPermission(@PathVariable String action) {
        try {
            boolean allowed = switch (action.toLowerCase()) {
                case "create-flows" -> environmentConfig.canCreateFlows();
                case "create-adapters" -> environmentConfig.canCreateAdapters();
                case "modify-adapter-config" -> environmentConfig.canModifyAdapterConfig();
                case "import-export-flows" -> environmentConfig.canImportExportFlows();
                case "deploy-flows" -> environmentConfig.canDeployFlows();
                default -> false;
            };
            
            return ResponseEntity.ok(ApiResponse.success("Permission checked", allowed));
        } catch (Exception e) {
            logger.error("Error checking permission for action: {}", action, e);
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Failed to check permission: " + e.getMessage()));
        }
    }
    
    /**
     * Request DTO for updating environment configuration
     */
    public static class UpdateEnvironmentRequest {
        private String type;
        private Boolean enforceRestrictions;
        private String restrictionMessage;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Boolean getEnforceRestrictions() { return enforceRestrictions; }
        public void setEnforceRestrictions(Boolean enforceRestrictions) { this.enforceRestrictions = enforceRestrictions; }
        
        public String getRestrictionMessage() { return restrictionMessage; }
        public void setRestrictionMessage(String restrictionMessage) { this.restrictionMessage = restrictionMessage; }
    }
}