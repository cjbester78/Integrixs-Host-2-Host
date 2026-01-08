package com.integrixs.backend.config;

import com.integrixs.shared.enums.EnvironmentType;
import com.integrixs.backend.service.EnvironmentConfigurationService;
import com.integrixs.backend.service.EnvironmentConfigurationService.EnvironmentConfiguration;
import com.integrixs.backend.service.EnvironmentConfigurationService.EnvironmentOperation;
import com.integrixs.backend.service.EnvironmentConfigurationService.EnvironmentRestrictions;
import com.integrixs.backend.service.EnvironmentConfigurationService.EnvironmentUpdateRequest;
import com.integrixs.backend.service.EnvironmentConfigurationService.OperationPermissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Environment configuration management
 * Provides environment-based feature restrictions and permissions
 * Refactored to use proper OOP patterns with dependency injection and immutable objects
 */
@Component
public class EnvironmentConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentConfig.class);
    
    private final EnvironmentConfigurationService environmentConfigurationService;
    
    public EnvironmentConfig(EnvironmentConfigurationService environmentConfigurationService) {
        this.environmentConfigurationService = environmentConfigurationService;
    }
    
    @PostConstruct
    public void initialize() {
        EnvironmentConfiguration config = environmentConfigurationService.getCurrentEnvironment();
        logger.info("Environment configuration loaded: {} (restrictions: {})", 
                   config.type(), config.enforceRestrictions());
    }
    
    /**
     * Load configuration from service
     * @deprecated Use environmentConfigurationService.getCurrentEnvironment() directly
     */
    @Deprecated
    public void loadConfiguration() {
        logger.debug("loadConfiguration() is deprecated, configuration is managed by EnvironmentConfigurationService");
        // Delegate to service for backward compatibility
        EnvironmentConfiguration config = environmentConfigurationService.getCurrentEnvironment();
        logger.debug("Environment config reloaded: {}", config.type());
    }
    
    /**
     * Get current environment type
     */
    public EnvironmentType getCurrentEnvironment() {
        return environmentConfigurationService.getCurrentEnvironment().type();
    }
    
    /**
     * Check if environment restrictions are enforced
     */
    public boolean isEnforceRestrictions() {
        return environmentConfigurationService.getCurrentEnvironment().enforceRestrictions();
    }
    
    /**
     * Get restriction message template
     */
    public String getRestrictionMessage() {
        return environmentConfigurationService.getCurrentEnvironment().restrictionMessage();
    }
    
    /**
     * Get formatted restriction message for current environment
     */
    public String getFormattedRestrictionMessage() {
        return environmentConfigurationService.getCurrentEnvironment().getFormattedRestrictionMessage();
    }
    
    /**
     * Check if flow creation is allowed
     */
    public boolean canCreateFlows() {
        OperationPermissionResult result = environmentConfigurationService.checkOperationPermission(EnvironmentOperation.CREATE_FLOW);
        return result.permitted();
    }
    
    /**
     * Check if adapter creation is allowed
     */
    public boolean canCreateAdapters() {
        OperationPermissionResult result = environmentConfigurationService.checkOperationPermission(EnvironmentOperation.CREATE_ADAPTER);
        return result.permitted();
    }
    
    /**
     * Check if adapter configuration can be modified
     */
    public boolean canModifyAdapterConfig() {
        OperationPermissionResult result = environmentConfigurationService.checkOperationPermission(EnvironmentOperation.MODIFY_ADAPTER_CONFIG);
        return result.permitted();
    }
    
    /**
     * Check if flow import/export is allowed
     */
    public boolean canImportExportFlows() {
        OperationPermissionResult result = environmentConfigurationService.checkOperationPermission(EnvironmentOperation.IMPORT_EXPORT_FLOWS);
        return result.permitted();
    }
    
    /**
     * Check if flow deployment is allowed
     */
    public boolean canDeployFlows() {
        OperationPermissionResult result = environmentConfigurationService.checkOperationPermission(EnvironmentOperation.DEPLOY_FLOWS);
        return result.permitted();
    }
    
    /**
     * Get all environment permissions
     */
    public Map<String, Boolean> getAllPermissions() {
        EnvironmentRestrictions restrictions = environmentConfigurationService.getEnvironmentRestrictions();
        Map<String, Boolean> permissions = new HashMap<>();
        permissions.put("canCreateFlows", restrictions.canCreateFlows());
        permissions.put("canCreateAdapters", restrictions.canCreateAdapters());
        permissions.put("canModifyAdapterConfig", restrictions.canModifyAdapterConfig());
        permissions.put("canImportExportFlows", restrictions.canImportExportFlows());
        permissions.put("canDeployFlows", restrictions.canDeployFlows());
        permissions.put("canDeleteData", restrictions.canDeleteData());
        permissions.put("canModifySystemConfig", restrictions.canModifySystemConfig());
        return permissions;
    }
    
    /**
     * Get environment information for API responses
     */
    public Map<String, Object> getEnvironmentInfo() {
        EnvironmentConfiguration config = environmentConfigurationService.getCurrentEnvironment();
        Map<String, Object> info = new HashMap<>();
        info.put("type", config.type().name());
        info.put("displayName", config.displayName());
        info.put("description", config.description());
        info.put("enforceRestrictions", config.enforceRestrictions());
        info.put("restrictionMessage", config.restrictionMessage());
        info.put("permissions", getAllPermissions());
        info.put("lastUpdated", config.lastUpdated());
        info.put("updatedBy", config.updatedBy());
        return info;
    }
    
    /**
     * Update environment configuration
     * @deprecated Use EnvironmentConfigurationService.updateEnvironment() instead
     */
    @Deprecated
    public void updateEnvironment(EnvironmentType newType, Boolean newEnforceRestrictions, String newMessage) {
        logger.warn("updateEnvironment() is deprecated, using EnvironmentConfigurationService");
        
        EnvironmentUpdateRequest request = EnvironmentUpdateRequest.builder()
            .type(newType)
            .enforceRestrictions(newEnforceRestrictions)
            .restrictionMessage(newMessage)
            .userId("LEGACY_UPDATE")
            .reason("Legacy environment update")
            .build();
        
        environmentConfigurationService.updateEnvironment(request);
        
        logger.info("Environment configuration updated via legacy method");
    }
}